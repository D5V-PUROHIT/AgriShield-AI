package com.agrishield.ai.ui.detection;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.agrishield.ai.R;
import com.agrishield.ai.ml.TFliteClassifier;
import com.agrishield.ai.models.PredictionResult;
import com.agrishield.ai.ui.result.ResultActivity;
import com.agrishield.ai.utils.LocaleHelper;

import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DetectionActivity extends AppCompatActivity {

    private ImageView ivPreview;
    private LinearLayout layoutLoading;
    private LinearLayout layoutLowConfidence;

    private TFliteClassifier classifier;
    private String imagePath;

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

        findViewById(R.id.btn_retake).setOnClickListener(v -> finish());

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
            public void onSuccess(List<PredictionResult> predictions) {
                runOnUiThread(() -> {
                    layoutLoading.setVisibility(View.GONE);
                    if (predictions != null && !predictions.isEmpty()) {
                        PredictionResult topResult = predictions.get(0);
                        if (topResult.getConfidence() < 0.40f) {
                            layoutLowConfidence.setVisibility(View.VISIBLE);
                            findViewById(R.id.btn_view_anyway).setOnClickListener(v -> launchResultScreen(topResult));
                        } else {
                            launchResultScreen(topResult);
                        }
                    } else {
                        layoutLowConfidence.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    layoutLoading.setVisibility(View.GONE);
                    layoutLowConfidence.setVisibility(View.VISIBLE);
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
