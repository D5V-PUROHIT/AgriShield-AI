package com.agrishield.ai.ui.result;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.agrishield.ai.R;
import com.agrishield.ai.database.DatabaseHelper;
import com.agrishield.ai.models.Disease;
import com.agrishield.ai.models.ScanHistory;
import com.agrishield.ai.utils.LocaleHelper;

import java.io.InputStream;
import java.util.List;

public class ResultActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private String imagePath;
    private String topLabel;
    private float topConfidence;
    private Disease disease;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_result);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        dbHelper = new DatabaseHelper(this);

        imagePath = getIntent().getStringExtra("image_path");
        topLabel = getIntent().getStringExtra("top_label");
        topConfidence = getIntent().getFloatExtra("top_confidence", 0f);

        disease = dbHelper.getDiseaseById(topLabel);

        ImageView ivResultImage = findViewById(R.id.iv_result_image);
        TextView tvCropDisease = findViewById(R.id.tv_crop_disease);
        TextView tvConfidenceLabel = findViewById(R.id.tv_confidence_label);
        ProgressBar pbConfidence = findViewById(R.id.pb_confidence);

        TextView tvSymptoms = findViewById(R.id.tv_symptoms);
        TextView tvCauses = findViewById(R.id.tv_causes);
        TextView tvPesticides = findViewById(R.id.tv_pesticides);
        TextView tvOrganicRemedies = findViewById(R.id.tv_organic_remedies);
        TextView tvPrecautions = findViewById(R.id.tv_precautions);
        TextView tvPreventionTips = findViewById(R.id.tv_prevention_tips);

        if (imagePath != null) {
            Bitmap b = loadBitmapFromPath(imagePath);
            if (b != null) ivResultImage.setImageBitmap(b);
        }

        int confidencePct = Math.round(topConfidence * 100);
        pbConfidence.setProgress(confidencePct);
        tvConfidenceLabel.setText(getString(R.string.confidence) + ": " + confidencePct + "%");

        if (disease != null) {
            tvCropDisease.setText(disease.getCropName() + " - " + disease.getDiseaseName());
            tvSymptoms.setText(disease.getSymptoms());
            tvCauses.setText(disease.getCauses());

            tvPesticides.setText(formatList(disease.getPesticides()));
            tvOrganicRemedies.setText(formatList(disease.getOrganicRemedies()));
            tvPrecautions.setText(formatList(disease.getPrecautions()));
            tvPreventionTips.setText(formatList(disease.getPreventionTips()));
        } else {
            tvCropDisease.setText(topLabel.replace("___", " - ").replace("_", " "));
            tvSymptoms.setText("Details not available.");
        }

        findViewById(R.id.btn_save_history).setOnClickListener(v -> saveToHistory());
    }

    private String formatList(List<String> items) {
        if (items == null || items.isEmpty()) return "None specified.";
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            sb.append("• ").append(item).append("\n");
        }
        return sb.toString().trim();
    }

    private Bitmap loadBitmapFromPath(String path) {
        try {
            if (path.startsWith("content://")) {
                Uri uri = Uri.parse(path);
                InputStream is = getContentResolver().openInputStream(uri);
                return BitmapFactory.decodeStream(is);
            } else {
                return BitmapFactory.decodeFile(path);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void saveToHistory() {
        ScanHistory scan = new ScanHistory();
        scan.setImagePath(imagePath);
        scan.setCropName(disease != null ? disease.getCropName() : "Unknown");
        scan.setDiseaseName(disease != null ? disease.getDiseaseName() : topLabel);
        scan.setConfidence(topConfidence);
        scan.setTimestamp(System.currentTimeMillis());

        dbHelper.saveScanHistory(scan);
        Toast.makeText(this, R.string.saved_to_history, Toast.LENGTH_SHORT).show();
    }
}
