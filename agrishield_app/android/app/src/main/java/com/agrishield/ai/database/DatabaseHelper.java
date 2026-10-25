package com.agrishield.ai.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.agrishield.ai.models.Disease;
import com.agrishield.ai.models.ScanHistory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "agrishield.db";
    private static final int DATABASE_VERSION = 1;

    // Table Diseases
    public static final String TABLE_DISEASES = "diseases";
    public static final String COL_DISEASE_ID = "id";
    public static final String COL_CROP_NAME = "crop_name";
    public static final String COL_DISEASE_NAME = "disease_name";
    public static final String COL_SYMPTOMS = "symptoms";
    public static final String COL_CAUSES = "causes";
    public static final String COL_PESTICIDES = "pesticides";
    public static final String COL_ORGANIC_REMEDIES = "organic_remedies";
    public static final String COL_PRECAUTIONS = "precautions";
    public static final String COL_PREVENTION_TIPS = "prevention_tips";

    // Table History
    public static final String TABLE_HISTORY = "history";
    public static final String COL_HIST_ID = "id";
    public static final String COL_HIST_IMAGE_PATH = "image_path";
    public static final String COL_HIST_CROP = "crop_name";
    public static final String COL_HIST_DISEASE = "disease_name";
    public static final String COL_HIST_CONFIDENCE = "confidence";
    public static final String COL_HIST_TIMESTAMP = "timestamp";

    private final Context context;
    private final Gson gson = new Gson();

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createDiseasesTable = "CREATE TABLE " + TABLE_DISEASES + " (" +
                COL_DISEASE_ID + " TEXT PRIMARY KEY, " +
                COL_CROP_NAME + " TEXT, " +
                COL_DISEASE_NAME + " TEXT, " +
                COL_SYMPTOMS + " TEXT, " +
                COL_CAUSES + " TEXT, " +
                COL_PESTICIDES + " TEXT, " +
                COL_ORGANIC_REMEDIES + " TEXT, " +
                COL_PRECAUTIONS + " TEXT, " +
                COL_PREVENTION_TIPS + " TEXT" +
                ")";

        String createHistoryTable = "CREATE TABLE " + TABLE_HISTORY + " (" +
                COL_HIST_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_HIST_IMAGE_PATH + " TEXT, " +
                COL_HIST_CROP + " TEXT, " +
                COL_HIST_DISEASE + " TEXT, " +
                COL_HIST_CONFIDENCE + " REAL, " +
                COL_HIST_TIMESTAMP + " INTEGER" +
                ")";

        db.execSQL(createDiseasesTable);
        db.execSQL(createHistoryTable);

        // Pre-populate disease_info.json into SQLite
        populateDiseasesFromJson(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DISEASES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
        onCreate(db);
    }

    private void populateDiseasesFromJson(SQLiteDatabase db) {
        try {
            InputStream is = context.getAssets().open("disease_info.json");
            InputStreamReader reader = new InputStreamReader(is);
            Type type = new TypeToken<Map<String, Disease>>() {}.getType();
            Map<String, Disease> diseaseMap = gson.fromJson(reader, type);

            if (diseaseMap != null) {
                db.beginTransaction();
                try {
                    for (Map.Entry<String, Disease> entry : diseaseMap.entrySet()) {
                        String id = entry.getKey();
                        Disease d = entry.getValue();

                        ContentValues values = new ContentValues();
                        values.put(COL_DISEASE_ID, id);
                        values.put(COL_CROP_NAME, d.getCropName());
                        values.put(COL_DISEASE_NAME, d.getDiseaseName());
                        values.put(COL_SYMPTOMS, d.getSymptoms());
                        values.put(COL_CAUSES, d.getCauses());
                        values.put(COL_PESTICIDES, gson.toJson(d.getPesticides()));
                        values.put(COL_ORGANIC_REMEDIES, gson.toJson(d.getOrganicRemedies()));
                        values.put(COL_PRECAUTIONS, gson.toJson(d.getPrecautions()));
                        values.put(COL_PREVENTION_TIPS, gson.toJson(d.getPreventionTips()));

                        db.insert(TABLE_DISEASES, null, values);
                    }
                    db.setTransactionSuccessful();
                    Log.d(TAG, "Successfully populated disease database with " + diseaseMap.size() + " records.");
                } finally {
                    db.endTransaction();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error seeding disease database: ", e);
        }
    }

    public Disease getDiseaseById(String diseaseId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_DISEASES, null, COL_DISEASE_ID + "=?",
                new String[]{diseaseId}, null, null, null);

        Disease disease = null;
        if (cursor != null && cursor.moveToFirst()) {
            disease = new Disease();
            disease.setId(cursor.getString(cursor.getColumnIndexOrThrow(COL_DISEASE_ID)));
            disease.setCropName(cursor.getString(cursor.getColumnIndexOrThrow(COL_CROP_NAME)));
            disease.setDiseaseName(cursor.getString(cursor.getColumnIndexOrThrow(COL_DISEASE_NAME)));
            disease.setSymptoms(cursor.getString(cursor.getColumnIndexOrThrow(COL_SYMPTOMS)));
            disease.setCauses(cursor.getString(cursor.getColumnIndexOrThrow(COL_CAUSES)));

            Type listType = new TypeToken<List<String>>() {}.getType();
            disease.setPesticides(gson.fromJson(cursor.getString(cursor.getColumnIndexOrThrow(COL_PESTICIDES)), listType));
            disease.setOrganicRemedies(gson.fromJson(cursor.getString(cursor.getColumnIndexOrThrow(COL_ORGANIC_REMEDIES)), listType));
            disease.setPrecautions(gson.fromJson(cursor.getString(cursor.getColumnIndexOrThrow(COL_PRECAUTIONS)), listType));
            disease.setPreventionTips(gson.fromJson(cursor.getString(cursor.getColumnIndexOrThrow(COL_PREVENTION_TIPS)), listType));

            cursor.close();
        }
        return disease;
    }

    public long saveScanHistory(ScanHistory scan) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_HIST_IMAGE_PATH, scan.getImagePath());
        values.put(COL_HIST_CROP, scan.getCropName());
        values.put(COL_HIST_DISEASE, scan.getDiseaseName());
        values.put(COL_HIST_CONFIDENCE, scan.getConfidence());
        values.put(COL_HIST_TIMESTAMP, scan.getTimestamp());

        return db.insert(TABLE_HISTORY, null, values);
    }

    public List<ScanHistory> getAllScanHistory() {
        List<ScanHistory> historyList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_HISTORY, null, null, null, null, null, COL_HIST_TIMESTAMP + " DESC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                ScanHistory scan = new ScanHistory();
                scan.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_HIST_ID)));
                scan.setImagePath(cursor.getString(cursor.getColumnIndexOrThrow(COL_HIST_IMAGE_PATH)));
                scan.setCropName(cursor.getString(cursor.getColumnIndexOrThrow(COL_HIST_CROP)));
                scan.setDiseaseName(cursor.getString(cursor.getColumnIndexOrThrow(COL_HIST_DISEASE)));
                scan.setConfidence(cursor.getFloat(cursor.getColumnIndexOrThrow(COL_HIST_CONFIDENCE)));
                scan.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(COL_HIST_TIMESTAMP)));

                historyList.add(scan);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return historyList;
    }

    public void deleteScanHistory(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_HISTORY, COL_HIST_ID + "=?", new String[]{String.valueOf(id)});
    }
}
