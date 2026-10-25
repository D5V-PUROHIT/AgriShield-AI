package com.agrishield.ai.ui.history;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.agrishield.ai.R;
import com.agrishield.ai.models.ScanHistory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(ScanHistory scan);
        void onDeleteClick(ScanHistory scan, int position);
    }

    private final Context context;
    private final List<ScanHistory> historyList;
    private final OnItemClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault());

    public HistoryAdapter(Context context, List<ScanHistory> historyList, OnItemClickListener listener) {
        this.context = context;
        this.historyList = historyList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScanHistory scan = historyList.get(position);

        holder.tvDisease.setText(scan.getDiseaseName());
        holder.tvCropConfidence.setText(scan.getCropName() + " • " + scan.getConfidencePercentage() + "% Confidence");
        holder.tvDate.setText(dateFormat.format(new Date(scan.getTimestamp())));

        if (scan.getImagePath() != null) {
            String path = scan.getImagePath();
            Object imageSource = path.startsWith("content://") ? Uri.parse(path) : new File(path);
            Glide.with(context)
                    .load(imageSource)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(holder.ivThumb);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(scan);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClick(scan, holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvDisease, tvCropConfidence, tvDate;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.iv_item_thumb);
            tvDisease = itemView.findViewById(R.id.tv_item_disease);
            tvCropConfidence = itemView.findViewById(R.id.tv_item_crop_confidence);
            tvDate = itemView.findViewById(R.id.tv_item_date);
            btnDelete = itemView.findViewById(R.id.btn_item_delete);
        }
    }
}
