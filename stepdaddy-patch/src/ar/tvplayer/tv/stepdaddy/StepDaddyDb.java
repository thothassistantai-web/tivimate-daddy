package ar.tvplayer.tv.stepdaddy;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/** Read-only TvPlayer.db helpers. */
final class StepDaddyDb {
    private StepDaddyDb() {
    }

    static int playlistCount(Context context) {
        return scalarInt(context, "SELECT COUNT(*) FROM playlists", null);
    }

    static int channelCount(Context context) {
        return scalarInt(context, "SELECT COUNT(*) FROM channels", null);
    }

    static JSONArray listChannels(Context context, int limit) {
        JSONArray array = new JSONArray();
        if (limit <= 0) {
            limit = 50;
        }
        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            database = openReadOnly(context);
            if (database == null) {
                return array;
            }
            cursor = database.rawQuery(
                "SELECT id, tvg_ch_no, name, custom_name FROM channels "
                    + "ORDER BY position_in_playlist ASC LIMIT ?",
                new String[]{String.valueOf(limit)}
            );
            while (cursor != null && cursor.moveToNext()) {
                JSONObject row = new JSONObject();
                row.put("id", cursor.getLong(0));
                if (!cursor.isNull(1)) {
                    row.put("tvg_ch_no", cursor.getInt(1));
                }
                String name = cursor.isNull(3) ? null : cursor.getString(3);
                if (name == null || name.trim().isEmpty()) {
                    name = cursor.getString(2);
                }
                row.put("name", name == null ? "" : name);
                array.put(row);
            }
        } catch (Exception error) {
            StepDaddyLog.w("listChannels failed", error);
        } finally {
            closeQuietly(cursor, database);
        }
        return array;
    }

    static long lookupChannelIdByName(Context context, String query) {
        if (query == null || query.trim().isEmpty()) {
            return -1L;
        }
        String needle = query.trim();
        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            database = openReadOnly(context);
            if (database == null) {
                return -1L;
            }
            cursor = database.rawQuery(
                "SELECT id FROM channels WHERE custom_name LIKE ? OR name LIKE ? "
                    + "ORDER BY position_in_playlist ASC LIMIT 1",
                new String[]{"%" + needle + "%", "%" + needle + "%"}
            );
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } catch (Exception error) {
            StepDaddyLog.w("lookupChannelIdByName failed", error);
        } finally {
            closeQuietly(cursor, database);
        }
        return -1L;
    }

    static ChannelInfo lookupChannelInfo(Context context, long channelId) {
        if (channelId <= 0L) {
            return null;
        }
        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            database = openReadOnly(context);
            if (database == null) {
                return null;
            }
            cursor = database.rawQuery(
                "SELECT id, tvg_ch_no, name, custom_name FROM channels WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(channelId)}
            );
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.isNull(3) ? null : cursor.getString(3);
                if (name == null || name.trim().isEmpty()) {
                    name = cursor.getString(2);
                }
                int chNo = cursor.isNull(1) ? -1 : cursor.getInt(1);
                return new ChannelInfo(channelId, chNo, name == null ? "" : name);
            }
        } catch (Exception error) {
            StepDaddyLog.w("lookupChannelInfo failed", error);
        } finally {
            closeQuietly(cursor, database);
        }
        return null;
    }

    static ChannelInfo lookupChannelInfoByNumber(Context context, int channelNumber) {
        long id = lookupChannelId(context, channelNumber);
        if (id <= 0L) {
            return null;
        }
        return lookupChannelInfo(context, id);
    }

    static long lookupChannelId(Context context, int channelNumber) {
        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            database = openReadOnly(context);
            if (database == null) {
                return -1L;
            }
            cursor = database.rawQuery(
                "SELECT id FROM channels WHERE tvg_ch_no = ? ORDER BY id LIMIT 1",
                new String[]{String.valueOf(channelNumber)}
            );
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
            if (cursor != null) {
                cursor.close();
            }
            cursor = database.rawQuery(
                "SELECT id FROM channels WHERE position_in_playlist = ? ORDER BY id LIMIT 1",
                new String[]{String.valueOf(channelNumber - 1)}
            );
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } catch (Exception error) {
            StepDaddyLog.w("lookupChannelId failed", error);
        } finally {
            closeQuietly(cursor, database);
        }
        return -1L;
    }

    static final class ChannelInfo {
        final long id;
        final int tvgChNo;
        final String name;

        ChannelInfo(long id, int tvgChNo, String name) {
            this.id = id;
            this.tvgChNo = tvgChNo;
            this.name = name;
        }
    }

    private static int scalarInt(Context context, String sql, String[] args) {
        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            database = openReadOnly(context);
            if (database == null) {
                return 0;
            }
            cursor = database.rawQuery(sql, args);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Exception error) {
            StepDaddyLog.w("scalarInt failed", error);
        } finally {
            closeQuietly(cursor, database);
        }
        return 0;
    }

    private static SQLiteDatabase openReadOnly(Context context) {
        File dbFile = context.getDatabasePath("TvPlayer.db");
        if (!dbFile.exists() || dbFile.length() == 0L) {
            return null;
        }
        return SQLiteDatabase.openDatabase(
            dbFile.getPath(),
            null,
            SQLiteDatabase.OPEN_READONLY
        );
    }

    private static void closeQuietly(Cursor cursor, SQLiteDatabase database) {
        if (cursor != null) {
            cursor.close();
        }
        if (database != null) {
            database.close();
        }
    }
}
