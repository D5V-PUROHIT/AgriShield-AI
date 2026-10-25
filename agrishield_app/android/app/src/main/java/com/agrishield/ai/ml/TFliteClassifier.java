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

    public interface InferenceCallback {
        void onSuccess(List<PredictionResult> predictions);
        void onError(Exception e);
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

    public void classifyImageAsync(Bitmap originalBitmap, InferenceCallback callback) {
        executor.execute(() -> {
            try {
                List<PredictionResult> results = classifyImageSync(originalBitmap);
                callback.onSuccess(results);
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

        // Convert Bitmap to ByteBuffer normalized [-1.0, 1.0]
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.getWidth(), 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight());

        int pixel = 0;
        for (int i = 0; i < INPUT_SIZE; ++i) {
            for (int j = 0; j < INPUT_SIZE; ++j) {
                final int val = intValues[pixel++];
                byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
                byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
                byteBuffer.putFloat((val & 0xFF) / 255.0f);
            }
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

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        executor.shutdown();
    }
}
