package com.agrishield.ai.ui.history;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.agrishield.ai.R;
import com.agrishield.ai.database.DatabaseHelper;
import com.agrishield.ai.models.ScanHistory;
import com.agrishield.ai.ui.detection.DetectionActivity;
import com.agrishield.ai.utils.LocaleHelper;

import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private RecyclerView rvHistoryFull;
    private TextView tvEmptyHistoryFull;
    private HistoryAdapter adapter;
    private List<ScanHistory> historyList;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_history);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        dbHelper = new DatabaseHelper(this);

        rvHistoryFull = findViewById(R.id.rv_history_full);
        tvEmptyHistoryFull = findViewById(R.id.tv_empty_history_full);

        rvHistoryFull.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    private void loadHistory() {
        historyList = dbHelper.getAllScanHistory();
        if (historyList.isEmpty()) {
            tvEmptyHistoryFull.setVisibility(View.VISIBLE);
            rvHistoryFull.setVisibility(View.GONE);
        } else {
            tvEmptyHistoryFull.setVisibility(View.GONE);
            rvHistoryFull.setVisibility(View.VISIBLE);

            adapter = new HistoryAdapter(this, historyList, new HistoryAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(ScanHistory scan) {
                    Intent intent = new Intent(HistoryActivity.this, DetectionActivity.class);
                    intent.putExtra("image_path", scan.getImagePath());
                    startActivity(intent);
                }

                @Override
                public void onDeleteClick(ScanHistory scan, int position) {
                    dbHelper.deleteScanHistory(scan.getId());
                    historyList.remove(position);
                    adapter.notifyItemRemoved(position);
                    Toast.makeText(HistoryActivity.this, R.string.scan_deleted, Toast.LENGTH_SHORT).show();

                    if (historyList.isEmpty()) {
                        tvEmptyHistoryFull.setVisibility(View.VISIBLE);
                        rvHistoryFull.setVisibility(View.GONE);
                    }
                }
            });
            rvHistoryFull.setAdapter(adapter);
        }
    }
}
