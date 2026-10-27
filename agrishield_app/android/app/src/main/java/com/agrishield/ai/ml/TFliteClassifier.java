package com.agrishield.ai.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import com.agrishield.ai.database.DatabaseHelper;
import com.agrishield.ai.models.Disease;
import com.agrishield.ai.models.PredictionResult;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TFliteClassifier {

    private static final String TAG = "TFliteClassifier";
    private static final String MODEL_FILE = "models/plant_disease_model.tflite";
    private static final String LABELS_FILE = "labels.txt";

    private static final int INPUT_SIZE = 224;
    private static final int PIXEL_SIZE = 3; // RGB
    private static final float IMAGE_MEAN = 127.5f;
    private static final float IMAGE_STD = 127.5f;

    private Interpreter interpreter;
    private List<String> labels;
    private final DatabaseHelper dbHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class BotanicalValidationResult {
        private final boolean isPlantSubject;
        private final float botanicalRatio;
        private final String status; // "VALID_CROP", "HUMAN_DETECTED", "NON_LIVING_OBJECT", "INSUFFICIENT_FOLIAGE"

        public BotanicalValidationResult(boolean isPlantSubject, float botanicalRatio, String status) {
            this.isPlantSubject = isPlantSubject;
            this.botanicalRatio = botanicalRatio;
            this.status = status;
        }

        public boolean isPlantSubject() { return isPlantSubject; }
        public float getBotanicalRatio() { return botanicalRatio; }
        public String getStatus() { return status; }
    }

    public interface InferenceCallback {
        void onSuccess(List<PredictionResult> predictions, BotanicalValidationResult validation);
        void onError(Exception e);
    }

    public static BotanicalValidationResult validateBotanicalContent(Bitmap originalBitmap) {
        if (originalBitmap == null) {
            return new BotanicalValidationResult(false, 0f, "NULL_BITMAP");
        }

        // Downscale to 112x112 for rapid color spectrum inspection (< 5 ms)
        int sampleSize = 112;
        Bitmap sample = Bitmap.createScaledBitmap(originalBitmap, sampleSize, sampleSize, false);
        int[] pixels = new int[sampleSize * sampleSize];
        sample.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize);
        if (sample != originalBitmap) {
            sample.recycle();
        }

        int botanicalCount = 0;
        int skinCount = 0;
        int achromaticCount = 0;
        float[] hsv = new float[3];

        for (int p : pixels) {
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;

            android.graphics.Color.RGBToHSV(r, g, b, hsv);
            float h = hsv[0];
            float s = hsv[1];
            float v = hsv[2];

            // 1. Botanical Green / Crop Foliage (Chlorophyll range: 45° to 170°)
            boolean isGreenFoliage = (h >= 45f && h <= 170f && s >= 0.15f && v >= 0.12f);

            // 2. Chlorotic / Yellowing plant disease tissue (32° to 45°, vibrant yellow-green)
            boolean isYellowChlorotic = (h >= 32f && h < 45f && s >= 0.22f && v >= 0.25f);

            // 3. Necrotic Leaf Blight / Rust spots (15° to 32°, brown/russet on leaf tissue)
            boolean isNecroticSpot = (h >= 15f && h < 32f && s >= 0.20f && v >= 0.12f && v <= 0.65f && r > b && g > b);

            // 4. Botanical fruit pigmentation (e.g. red tomato with foliage)
            boolean isBotanicalFruitRed = (h >= 345f || h <= 14f) && s >= 0.40f && v >= 0.25f;

            if (isGreenFoliage || isYellowChlorotic || isNecroticSpot || isBotanicalFruitRed) {
                botanicalCount++;
            }

            // 5. Human Skin Detection (Skin hue cluster, R > G > B with red-green separation)
            boolean isSkin = ((h <= 30f || h >= 335f) && s >= 0.16f && s <= 0.68f && v >= 0.30f && v <= 0.95f && r > g && g > b && (r - g) > 15);
            if (isSkin) {
                skinCount++;
            }

            // 6. Artificial achromatic (pure black headphones, dark plastic, neutral gray/white desk)
            boolean isAchromatic = (s < 0.14f || v < 0.14f);
            if (isAchromatic) {
                achromaticCount++;
            }
        }

        int total = pixels.length;
        float botanicalRatio = (float) botanicalCount / total;
        float skinRatio = (float) skinCount / total;
        float achromaticRatio = (float) achromaticCount / total;

        // Human detection gate: skin tones prominent with negligible botanical presence
        if (skinRatio > 0.20f && botanicalRatio < 0.10f) {
            return new BotanicalValidationResult(false, botanicalRatio, "HUMAN_DETECTED");
        }

        // Artificial object gate: black headphones, metallic objects, neutral desk
        if (achromaticRatio > 0.60f && botanicalRatio < 0.08f) {
            return new BotanicalValidationResult(false, botanicalRatio, "NON_LIVING_OBJECT");
        }

        // Minimum botanical foliage presence threshold: at least 10% of image must have plant characteristics
        if (botanicalRatio < 0.10f) {
            return new BotanicalValidationResult(false, botanicalRatio, "INSUFFICIENT_FOLIAGE");
        }

        return new BotanicalValidationResult(true, botanicalRatio, "VALID_CROP");
    }

    public void classifyImageAsync(Bitmap originalBitmap, InferenceCallback callback) {
        executor.execute(() -> {
            try {
                BotanicalValidationResult validation = validateBotanicalContent(originalBitmap);
                List<PredictionResult> results = classifyImageSync(originalBitmap);
                callback.onSuccess(results, validation);
            } catch (Exception e) {
                Log.e(TAG, "Inference error: ", e);
                callback.onError(e);
            }
        });
    }

    public List<PredictionResult> classifyImageSync(Bitmap originalBitmap) {
        if (interpreter == null || labels == null) {
            throw new IllegalStateException("Classifier not initialized");
        }

        // Resize bitmap to 224x224
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, INPUT_SIZE, INPUT_SIZE, true);

        // Convert Bitmap to ByteBuffer normalized [0.0, 1.0] matching model training (rescale = 1.0 / 255.0)
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.getWidth(), 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight());

        int pixel = 0;
        for (int i = 0; i < INPUT_SIZE; ++i) {
            for (int j = 0; j < INPUT_SIZE; ++j) {
                final int val = intValues[pixel++];
                // PlantVillage MobileNetV2 was trained with rescale=1./255 -> [0.0, 1.0]
                byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
                byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
                byteBuffer.putFloat((val & 0xFF) / 255.0f);
            }
        }

        if (scaledBitmap != originalBitmap) {
            scaledBitmap.recycle();
        }

        // Run inference
        float[][] output = new float[1][labels.size()];
        interpreter.run(byteBuffer, output);

        // Process outputs into PredictionResult list
        List<PredictionResult> predictions = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String labelKey = labels.get(i);
            float confidence = output[0][i];
            PredictionResult res = new PredictionResult(labelKey, confidence);

            // Fetch disease info from SQLite
            Disease disease = dbHelper.getDiseaseById(labelKey);
            res.setDisease(disease);

            predictions.add(res);
        }

        // Sort descending by confidence score
        Collections.sort(predictions);

        // Return Top 3 predictions
        return predictions.subList(0, Math.min(3, predictions.size()));
    }

    public TFliteClassifier(Context context) {
        this.dbHelper = new DatabaseHelper(context);
        try {
            interpreter = new Interpreter(loadModelFile(context));
            labels = loadLabels(context);
            Log.d(TAG, "TFLite Classifier loaded successfully with " + labels.size() + " labels.");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TFLite Interpreter: ", e);
        }
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private List<String> loadLabels(Context context) throws IOException {
        List<String> labelList = new ArrayList<>();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(LABELS_FILE)));
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                labelList.add(line.trim());
            }
        }
        reader.close();
        return labelList;
    }



    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        executor.shutdown();
    }
}
