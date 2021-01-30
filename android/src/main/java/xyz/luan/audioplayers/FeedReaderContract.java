package xyz.luan.audioplayers;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

public final class FeedReaderContract {
    // To prevent someone from accidentally instantiating the contract class,
    // make the constructor private.
    private FeedReaderContract() {}

    /* Inner class that defines the table contents */
    public static class FeedEntry implements BaseColumns {
        public static final String TABLE_NAME = "tracker";
        public static final String COLUMN_NAME_BOOK_ID = "book_id";
        public static final String COLUMN_NAME_CHAPTER_INDEX = "chapter_index";
        public static final String COLUMN_NAME_TIME = "time";

    }

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + FeedEntry.TABLE_NAME + " (" +
                    FeedEntry.COLUMN_NAME_BOOK_ID + " INTEGER," +
                    FeedEntry.COLUMN_NAME_CHAPTER_INDEX + " INTEGER," +
                    FeedEntry.COLUMN_NAME_TIME + " REAL, UNIQUE(book_id, chapter_index) ON CONFLICT REPLACE)";

    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + FeedEntry.TABLE_NAME;


}


