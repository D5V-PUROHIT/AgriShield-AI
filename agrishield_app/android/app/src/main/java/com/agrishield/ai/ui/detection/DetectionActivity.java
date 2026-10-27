package com.agrishield.ai.ui.detection;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.agrishield.ai.R;
import com.agrishield.ai.ml.TFliteClassifier;
import com.agrishield.ai.models.PredictionResult;
import com.agrishield.ai.ui.result.ResultActivity;
import com.agrishield.ai.utils.LocaleHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DetectionActivity extends AppCompatActivity {

    private ImageView ivPreview;
    private LinearLayout layoutLoading;
    private LinearLayout layoutLowConfidence;
    private LinearLayout layoutNotAPlant;

    private TFliteClassifier classifier;
    private String imagePath;

    private Uri photoUri;
    private String currentPhotoPath;

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), isSuccess -> {
                if (Boolean.TRUE.equals(isSuccess) && currentPhotoPath != null) {
                    processNewImagePath(currentPhotoPath);
                }
            });

    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        processNewImagePath(selectedImageUri.toString());
                    }
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    dispatchTakePictureIntent();
                } else {
                    Toast.makeText(this, "Camera permission is required to capture photos.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detection);

        ivPreview = findViewById(R.id.iv_preview);
        layoutLoading = findViewById(R.id.layout_loading);
        layoutLowConfidence = findViewById(R.id.layout_low_confidence);
        layoutNotAPlant = findViewById(R.id.layout_not_a_plant);

        findViewById(R.id.btn_retake).setOnClickListener(v -> checkCameraPermissionAndOpen());
        findViewById(R.id.btn_not_plant_retake).setOnClickListener(v -> checkCameraPermissionAndOpen());
        findViewById(R.id.btn_not_plant_gallery).setOnClickListener(v -> openGallery());

        imagePath = getIntent().getStringExtra("image_path");
        if (imagePath == null) {
            Toast.makeText(this, "No image provided.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        imagePath = persistContentUriIfNeeded(imagePath);

        Bitmap bitmap = loadBitmapFromPath(imagePath);
        if (bitmap != null) {
            ivPreview.setImageBitmap(bitmap);
            classifier = new TFliteClassifier(this);
            runInference(bitmap);
        } else {
            Toast.makeText(this, "Failed to load image.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void processNewImagePath(String path) {
        imagePath = persistContentUriIfNeeded(path);
        Bitmap bitmap = loadBitmapFromPath(imagePath);
        if (bitmap != null) {
            ivPreview.setImageBitmap(bitmap);
            layoutNotAPlant.setVisibility(View.GONE);
            layoutLowConfidence.setVisibility(View.GONE);
            layoutLoading.setVisibility(View.VISIBLE);
            if (classifier == null) {
                classifier = new TFliteClassifier(this);
            }
            runInference(bitmap);
        }
    }

    private void checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            dispatchTakePictureIntent();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void dispatchTakePictureIntent() {
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            Toast.makeText(this, "Error creating image file: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        if (photoFile != null) {
            try {
                photoUri = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        photoFile);
                cameraLauncher.launch(photoUri);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No camera application found on this device.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to launch camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private String persistContentUriIfNeeded(String path) {
        if (path != null && path.startsWith("content://")) {
            try {
                Uri uri = Uri.parse(path);
                InputStream is = getContentResolver().openInputStream(uri);
                if (is != null) {
                    File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                    File destFile = new File(storageDir, "SCAN_" + timeStamp + ".jpg");
                    FileOutputStream fos = new FileOutputStream(destFile);
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                    fos.flush();
                    fos.close();
                    is.close();
                    return destFile.getAbsolutePath();
                }
            } catch (Exception ignored) {
            }
        }
        return path;
    }

    private Bitmap loadBitmapFromPath(String path) {
        try {
            Bitmap bitmap;
            if (path.startsWith("content://")) {
                Uri uri = Uri.parse(path);
                bitmap = decodeSampledBitmapFromUri(uri, 1024, 1024);
            } else {
                bitmap = decodeSampledBitmapFromFile(path, 1024, 1024);
                bitmap = rotateBitmapIfNeeded(path, bitmap);
            }
            return bitmap;
        } catch (Throwable t) {
            return null;
        }
    }

    private Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    private Bitmap decodeSampledBitmapFromUri(Uri uri, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            InputStream is = getContentResolver().openInputStream(uri);
            if (is != null) {
                BitmapFactory.decodeStream(is, null, options);
                is.close();
            }

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            if (is != null) {
                is.close();
            }
            return bitmap;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private Bitmap rotateBitmapIfNeeded(String path, Bitmap bitmap) {
        if (bitmap == null) return null;
        try {
            ExifInterface exif = new ExifInterface(path);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int rotationAngle = 0;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotationAngle = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotationAngle = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotationAngle = 270;
                    break;
                default:
                    break;
            }
            if (rotationAngle != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationAngle);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) {
                    bitmap.recycle();
                }
                return rotated;
            }
        } catch (Throwable ignored) {
        }
        return bitmap;
    }

    private void runInference(Bitmap bitmap) {
        classifier.classifyImageAsync(bitmap, new TFliteClassifier.InferenceCallback() {
            @Override
            public void onSuccess(List<PredictionResult> predictions, TFliteClassifier.BotanicalValidationResult validation) {
                runOnUiThread(() -> {
                    layoutLoading.setVisibility(View.GONE);

                    // Gate 1: Check botanical plant/foliage presence
                    if (validation == null || !validation.isPlantSubject()) {
                        layoutNotAPlant.setVisibility(View.VISIBLE);
                        layoutLowConfidence.setVisibility(View.GONE);
                        return;
                    }

                    // Gate 2: Plant foliage verified, evaluate prediction confidence
                    if (predictions != null && !predictions.isEmpty()) {
                        PredictionResult topResult = predictions.get(0);
                        if (topResult.getConfidence() < 0.45f) {
                            // High entropy / unrecognizable foliar condition
                            layoutNotAPlant.setVisibility(View.VISIBLE);
                            layoutLowConfidence.setVisibility(View.GONE);
                        } else if (topResult.getConfidence() < 0.60f) {
                            // Uncertain diagnosis: show warning with option to view best match
                            layoutLowConfidence.setVisibility(View.VISIBLE);
                            layoutNotAPlant.setVisibility(View.GONE);
                            findViewById(R.id.btn_view_anyway).setOnClickListener(v -> launchResultScreen(topResult));
                        } else {
                            // Confident diagnosis: proceed to advisory screen
                            launchResultScreen(topResult);
                        }
                    } else {
                        layoutNotAPlant.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    layoutLoading.setVisibility(View.GONE);
                    layoutNotAPlant.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void launchResultScreen(PredictionResult result) {
        Intent intent = new Intent(DetectionActivity.this, ResultActivity.class);
        intent.putExtra("image_path", imagePath);
        intent.putExtra("top_label", result.getLabel());
        intent.putExtra("top_confidence", result.getConfidence());
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (classifier != null) {
            classifier.close();
        }
    }
}
