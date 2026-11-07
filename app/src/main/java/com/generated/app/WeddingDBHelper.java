package com.generated.app;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

public class WeddingDBHelper extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "wedding_database.db";

    private static final String SQL_CREATE_GALLERY_TABLE =
            "CREATE TABLE " + GalleryEntry.TABLE_NAME + " (" +
                    GalleryEntry._ID + " INTEGER PRIMARY KEY," +
                    GalleryEntry.COLUMN_NAME_URI + " TEXT UNIQUE)";

    private static final String SQL_DELETE_GALLERY_TABLE =
            "DROP TABLE IF EXISTS " + GalleryEntry.TABLE_NAME;

    public WeddingDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_GALLERY_TABLE);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_GALLERY_TABLE);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public static class GalleryEntry implements BaseColumns {
        public static final String TABLE_NAME = "gallery_images";
        public static final String COLUMN_NAME_URI = "image_uri";
    }
}
