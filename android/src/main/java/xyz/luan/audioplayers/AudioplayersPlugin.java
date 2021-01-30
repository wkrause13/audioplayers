package xyz.luan.audioplayers;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.PluginRegistry.Registrar;

public class AudioplayersPlugin implements MethodCallHandler, FlutterPlugin {

    private static final Logger LOGGER = Logger.getLogger(AudioplayersPlugin.class.getCanonicalName());

    private MethodChannel channel;
    private final Map<String, Player> mediaPlayers = new HashMap<>();
    private final Handler handler = new Handler();
    private Runnable positionUpdates;
    private Context context;
    private boolean seekFinish;
    private FeedReaderDbHelper dbHelper;
    private SQLiteDatabase db;
    private Integer book_id;
    private Integer chapter_index;





    public static void registerWith(final Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "xyz.luan/audioplayers");
        channel.setMethodCallHandler(new AudioplayersPlugin(channel, registrar.activeContext()));
    }

    private AudioplayersPlugin(final MethodChannel channel, Context context) {
        this.channel = channel;
        this.channel.setMethodCallHandler(this);
        this.context = context;
        this.seekFinish = false;
    }

    public AudioplayersPlugin() {}

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        final MethodChannel channel = new MethodChannel(binding.getBinaryMessenger(), "xyz.luan/audioplayers");
        this.channel = channel;
        this.context = binding.getApplicationContext();
        this.seekFinish = false;
        channel.setMethodCallHandler(this);

        this.dbHelper = new FeedReaderDbHelper(this.context);
        this.db = this.dbHelper.getWritableDatabase();

    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        this.dbHelper.close();
    }

    @Override
    public void onMethodCall(final MethodCall call, final MethodChannel.Result response) {
        try {
            handleMethodCall(call, response);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error!", e);
            response.error("Unexpected error!", e.getMessage(), e);
        }
    }

    private void handleMethodCall(final MethodCall call, final MethodChannel.Result response) {
        final String playerId = call.argument("playerId");
        final String mode = call.argument("mode");
        final Player player = getPlayer(playerId, mode);
        switch (call.method) {
            case "play": {
                final String url = call.argument("url");
                final double volume = call.argument("volume");
                final Integer position = call.argument("position");
                final boolean respectSilence = call.argument("respectSilence");
                final boolean isLocal = call.argument("isLocal");
                final boolean stayAwake = call.argument("stayAwake");
                player.configAttributes(respectSilence, stayAwake, context.getApplicationContext());
                player.setVolume(volume);
                player.setUrl(url, isLocal, context.getApplicationContext());
                if (position != null && !mode.equals("PlayerMode.LOW_LATENCY")) {
                    player.seek(position);
                }
                player.play(context.getApplicationContext());
                break;
            }
            case "resume": {
                player.play(context.getApplicationContext());
                break;
            }
            case "pause": {
                player.pause();
                break;
            }
            case "stop": {
                player.stop();
                break;
            }
            case "release": {
                player.release();
                break;
            }
            case "seek": {
                final Integer position = call.argument("position");
                player.seek(position);
                break;
            }
            case "setVolume": {
                final double volume = call.argument("volume");
                player.setVolume(volume);
                break;
            }
            case "setUrl": {
                final String url = call.argument("url");
                final boolean isLocal = call.argument("isLocal");
                player.setUrl(url, isLocal, context.getApplicationContext());
                break;
            }
            case "setBookInfo": {
                final int bookId = call.argument("bookId");
                final int chapterIndex = call.argument("chapterIndex");
                this.book_id = bookId;
                this.chapter_index = chapterIndex;
                break;
            }
            case "setPlaybackRate": {
                final double rate = call.argument("playbackRate");
                response.success(player.setRate(rate));
                return;
            }
            case "getDuration": {
                response.success(player.getDuration());
                return;
            }
            case "getCurrentPosition": {
                response.success(player.getCurrentPosition());
                return;
            }
            case "setReleaseMode": {
                final String releaseModeName = call.argument("releaseMode");
                final ReleaseMode releaseMode = ReleaseMode.valueOf(releaseModeName.substring("ReleaseMode.".length()));
                player.setReleaseMode(releaseMode);
                break;
            }
            case "earpieceOrSpeakersToggle": {
                final String playingRoute = call.argument("playingRoute");
                player.setPlayingRoute(playingRoute, context.getApplicationContext());
                break;
            }
            default: {
                response.notImplemented();
                return;
            }
        }
        response.success(1);
    }

    private Player getPlayer(String playerId, String mode) {
        if (!mediaPlayers.containsKey(playerId)) {
            Player player =
                    mode.equalsIgnoreCase("PlayerMode.MEDIA_PLAYER") ?
                            new WrappedMediaPlayer(this, playerId) :
                            new WrappedSoundPool(this, playerId);
            mediaPlayers.put(playerId, player);
        }
        return mediaPlayers.get(playerId);
    }

    public void handleIsPlaying(Player player) {
        startPositionUpdates();
    }

    public void handleDuration(Player player) {
        channel.invokeMethod("audio.onDuration", buildArguments(player.getPlayerId(), player.getDuration()));
    }

    public void handleCompletion(Player player) {
        channel.invokeMethod("audio.onComplete", buildArguments(player.getPlayerId(), true));
    }

    public void handleError(Player player, String message) {
        channel.invokeMethod("audio.onError", buildArguments(player.getPlayerId(), message));
    }

    public void handleSeekComplete(Player player) {
        this.seekFinish = true;
    }

    private void startPositionUpdates() {
        if (positionUpdates != null) {
            return;
        }
        positionUpdates = new UpdateCallback(mediaPlayers, channel, handler, this.db, this.book_id,this.chapter_index,this);
        handler.post(positionUpdates);
    }

    private void stopPositionUpdates() {
        positionUpdates = null;
        handler.removeCallbacksAndMessages(null);
    }

    private static Map<String, Object> buildArguments(String playerId, Object value) {
        Map<String, Object> result = new HashMap<>();
        result.put("playerId", playerId);
        result.put("value", value);
        return result;
    }

    private static final class UpdateCallback implements Runnable {

        private final WeakReference<Map<String, Player>> mediaPlayers;
        private final WeakReference<MethodChannel> channel;
        private final WeakReference<Handler> handler;
        private final WeakReference<SQLiteDatabase> db;
        private final WeakReference<Integer> book_id;
        private final WeakReference<Integer> chapter_index;
        private final WeakReference<AudioplayersPlugin> audioplayersPlugin;

        private UpdateCallback(final Map<String, Player> mediaPlayers,
                               final MethodChannel channel,
                               final Handler handler,
                               final SQLiteDatabase db,
                               final Integer book_id,
                               final Integer chapter_index,
                               final AudioplayersPlugin audioplayersPlugin) {
            this.mediaPlayers = new WeakReference<>(mediaPlayers);
            this.channel = new WeakReference<>(channel);
            this.handler = new WeakReference<>(handler);
            this.db = new WeakReference<>(db);
            this.book_id = new WeakReference<>(book_id);
            this.chapter_index = new WeakReference<>(chapter_index);
            this.audioplayersPlugin = new WeakReference<>(audioplayersPlugin);
        }

        @Override
        public void run() {
            final Map<String, Player> mediaPlayers = this.mediaPlayers.get();
            final MethodChannel channel = this.channel.get();
            final Handler handler = this.handler.get();
            final AudioplayersPlugin audioplayersPlugin = this.audioplayersPlugin.get();
            final SQLiteDatabase db =this.db.get();
            final Integer book_id =this.book_id.get();
            final Integer chapter_index =this.chapter_index.get();

            if (mediaPlayers == null || channel == null || handler == null || audioplayersPlugin == null) {
                if (audioplayersPlugin != null) {
                    audioplayersPlugin.stopPositionUpdates();
                }
                return;
            }

            boolean nonePlaying = true;
            for (Player player : mediaPlayers.values()) {
                if (!player.isActuallyPlaying()) {
                    continue;
                }
                try {
                    nonePlaying = false;
                    final String key = player.getPlayerId();
                    final int duration = player.getDuration();
                    final int time = player.getCurrentPosition();

                    ContentValues values = new ContentValues();
                    values.put(FeedReaderContract.FeedEntry.COLUMN_NAME_BOOK_ID, book_id);
                    values.put(FeedReaderContract.FeedEntry.COLUMN_NAME_CHAPTER_INDEX, chapter_index);
                    values.put(FeedReaderContract.FeedEntry.COLUMN_NAME_TIME, time);

                    
                    // Insert the new row, returning the primary key value of the new row
                    db.insert(FeedReaderContract.FeedEntry.TABLE_NAME, null, values);


                    channel.invokeMethod("audio.onDuration", buildArguments(key, duration));
                    channel.invokeMethod("audio.onCurrentPosition", buildArguments(key, time));
                    if (audioplayersPlugin.seekFinish) {
                        channel.invokeMethod("audio.onSeekComplete", buildArguments(player.getPlayerId(), true));
                        audioplayersPlugin.seekFinish = false;
                    }
                } catch (UnsupportedOperationException e) {

                }
            }

            if (nonePlaying) {
                audioplayersPlugin.stopPositionUpdates();
            } else {
                handler.postDelayed(this, 50);
            }
        }

    }
}

//public final class FeedReaderContract {
//    // To prevent someone from accidentally instantiating the contract class,
//    // make the constructor private.
//    private FeedReaderContract() {}
//
//    /* Inner class that defines the table contents */
//    public static class FeedEntry implements BaseColumns {
//        public static final String TABLE_NAME = "tracker";
//        public static final String COLUMN_NAME_BOOK_ID = "book_id";
//        public static final String COLUMN_NAME_CHAPTER_INDEX = "chapter_index";
//        public static final String COLUMN_NAME_TIME = "time";
//
//    }
//
//}









// private const val SQL_CREATE_ENTRIES =
//         "CREATE TABLE IF NOT EXISTS tracker (" +
//                 "book_id INTEGER," +
//                 "chapter_index INTEGER," +
//                 "time REAL, UNIQUE(book_id, chapter_index) ON CONFLICT REPLACE)"


// private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS tracker"

// class FeedReaderDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
//     override fun onCreate(db: SQLiteDatabase) {
//         db.execSQL(SQL_CREATE_ENTRIES)
//     }
//     override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
//         // This database is only a cache for online data, so its upgrade policy is
//         // to simply to discard the data and start over
//         db.execSQL(SQL_DELETE_ENTRIES)
//         onCreate(db)
//     }
//     override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
//         onUpgrade(db, oldVersion, newVersion)
//     }
//     companion object {
//         // If you change the database schema, you must increment the database version.
//         const val DATABASE_VERSION = 1
//         const val DATABASE_NAME = "tracker.db"
//     }
// }

