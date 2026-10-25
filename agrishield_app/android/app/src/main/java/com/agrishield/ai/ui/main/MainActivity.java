package com.agrishield.ai.ui.main;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.agrishield.ai.R;
import com.agrishield.ai.database.DatabaseHelper;
import com.agrishield.ai.models.ScanHistory;
import com.agrishield.ai.ui.about.AboutActivity;
import com.agrishield.ai.ui.detection.DetectionActivity;
import com.agrishield.ai.ui.history.HistoryActivity;
import com.agrishield.ai.ui.history.HistoryAdapter;
import com.agrishield.ai.utils.LocaleHelper;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private RecyclerView rvRecentScans;
    private TextView tvEmptyHistory;
    private HistoryAdapter historyAdapter;
    private List<ScanHistory> recentScansList;

    private Uri photoUri;
    private String currentPhotoPath;

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), isSuccess -> {
                if (Boolean.TRUE.equals(isSuccess) && currentPhotoPath != null) {
                    launchDetectionScreen(currentPhotoPath);
                }
            });

    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        launchDetectionScreen(selectedImageUri.toString());
                    }
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    dispatchTakePictureIntent();
                } else {
                    Toast.makeText(this, "Camera permission is required to capture leaf photos.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        dbHelper = new DatabaseHelper(this);

        findViewById(R.id.btn_take_photo).setOnClickListener(v -> checkCameraPermissionAndOpen());
        findViewById(R.id.btn_upload_gallery).setOnClickListener(v -> openGallery());
        findViewById(R.id.btn_view_all_history).setOnClickListener(v -> startActivity(new Intent(MainActivity.this, HistoryActivity.class)));

        rvRecentScans = findViewById(R.id.rv_recent_scans);
        tvEmptyHistory = findViewById(R.id.tv_empty_history);
        rvRecentScans.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecentScans();
    }

    private void loadRecentScans() {
        recentScansList = dbHelper.getAllScanHistory();
        if (recentScansList.isEmpty()) {
            tvEmptyHistory.setVisibility(View.VISIBLE);
            rvRecentScans.setVisibility(View.GONE);
        } else {
            tvEmptyHistory.setVisibility(View.GONE);
            rvRecentScans.setVisibility(View.VISIBLE);

            historyAdapter = new HistoryAdapter(this, recentScansList, new HistoryAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(ScanHistory scan) {
                    launchDetectionScreen(scan.getImagePath());
                }

                @Override
                public void onDeleteClick(ScanHistory scan, int position) {
                    dbHelper.deleteScanHistory(scan.getId());
                    recentScansList.remove(position);
                    historyAdapter.notifyItemRemoved(position);
                    if (recentScansList.isEmpty()) {
                        tvEmptyHistory.setVisibility(View.VISIBLE);
                        rvRecentScans.setVisibility(View.GONE);
                    }
                }
            });
            rvRecentScans.setAdapter(historyAdapter);
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

    private void launchDetectionScreen(String imagePath) {
        Intent intent = new Intent(MainActivity.this, DetectionActivity.class);
        intent.putExtra("image_path", imagePath);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_history) {
            startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            return true;
        } else if (id == R.id.action_language) {
            showLanguageDialog();
            return true;
        } else if (id == R.id.action_about) {
            startActivity(new Intent(MainActivity.this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showLanguageDialog() {
        String[] languages = {getString(R.string.english), getString(R.string.hindi), getString(R.string.gujarati)};
        String[] langCodes = {"en", "hi", "gu"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.select_language);
        builder.setItems(languages, (dialog, which) -> {
            String selectedLang = langCodes[which];
            LocaleHelper.setLocale(this, selectedLang);
            recreate();
        });
        builder.show();
    }
}
