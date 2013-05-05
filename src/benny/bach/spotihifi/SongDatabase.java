package benny.bach.spotihifi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONTokener;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class SongDatabase extends SQLiteOpenHelper{

	private static final int DATABASE_VERSION = 1;
	
	private static final String DATABASE_NAME = null;
	private static final String SONG_TABLE_NAME = "song";
	
	private static final String KEY_ID = "_id";
	private static final String KEY_TITLE = "title";
	private static final String KEY_ARTIST = "artist";
	private static final String KEY_ALBUM = "album";
	private static final String KEY_TRACK_NUMBER = "track_number";
	private static final String KEY_TRACK_ID = "track_id";
	private static final String KEY_PLAYLISTS = "playlists";
	
	private static final String SONG_TABLE_CREATE =
			"CREATE TABLE " + SONG_TABLE_NAME + " (" + 
	        KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
			KEY_TITLE + "," +
	        KEY_ARTIST + "," +
			KEY_ALBUM + "," +
	        KEY_TRACK_NUMBER + "," +
	        KEY_TRACK_ID + " UNIQUE," +
	        KEY_PLAYLISTS + ");";
	
	private static final String[] READ_SONG_PROJECTION = new String[] {
		KEY_ID,
		KEY_TITLE,
		KEY_ARTIST,
		KEY_ALBUM,
		KEY_TRACK_NUMBER,
		KEY_TRACK_ID,
		KEY_PLAYLISTS
	};

	private static final String[] READ_ARTISTS_PROJECTION = new String[] {
		KEY_ARTIST
	};

	private static final String[] READ_PLAYLISTS_PROJECTION = new String[] {
		KEY_PLAYLISTS
	};
		
	SongDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SONG_TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }
    
    public long insertSong(String title, String artist, String album, int track_number, String track_id, String playlists) {
    	SQLiteDatabase db = getWritableDatabase();
    	
    	ContentValues initialValues = new ContentValues();
    	initialValues.put(KEY_TITLE, title);
    	initialValues.put(KEY_ARTIST, artist);
    	initialValues.put(KEY_ALBUM, album);
    	initialValues.put(KEY_TRACK_NUMBER, track_number);
    	initialValues.put(KEY_TRACK_ID, track_id);
    	initialValues.put(KEY_PLAYLISTS, playlists);
 
    	//return db.insert(SONG_TABLE_NAME, null, initialValues);
    	return db.replace(SONG_TABLE_NAME, null, initialValues);
    }
    
    public Cursor querySongs(String where) {        
        SQLiteDatabase db = getReadableDatabase();
        
        Cursor c = db.query(SONG_TABLE_NAME, READ_SONG_PROJECTION, where, null, null, null, KEY_ARTIST + "," + KEY_ALBUM + "," + KEY_TRACK_NUMBER);
 
        return c;
    }
    
    public Cursor querySongById(long id) {        
        SQLiteDatabase db = getReadableDatabase();
        
        Cursor c = db.query(SONG_TABLE_NAME, READ_SONG_PROJECTION, KEY_ID + "=" + id, null, null, null, KEY_ARTIST);
 
        return c;
    }
    
    public ArrayList<String> queryArtistsAll() {        
        SQLiteDatabase db = getReadableDatabase();
        
        Cursor c = db.query(true, SONG_TABLE_NAME, READ_ARTISTS_PROJECTION, null, null, null, null, KEY_ARTIST, null);
 
        ArrayList<String> artists = new ArrayList<String>();
        
        if (c == null || !c.moveToFirst()) {
        	Log.i("kfjdk", "hmmm not artists");
        }
        else {
        	while(!c.isAfterLast()) {
        		artists.add(c.getString(c.getColumnIndex(KEY_ARTIST)));
        		c.moveToNext();
        	}
        }
                
        //return c;
        return artists;
    }

    
    public ArrayList<String> queryPlaylistsAll() {        
        SQLiteDatabase db = getReadableDatabase();
        
        Cursor c = db.query(true, SONG_TABLE_NAME, READ_PLAYLISTS_PROJECTION, null, null, null, null, null, null);

        	
        Set<String> values = new HashSet<String>();
        
        if (c == null || !c.moveToFirst()) {
        	Log.i("kfjdk", "hmmm no playlists");
        }
        else {
        	while(!c.isAfterLast()) 
        	{
        		try {
        			String s = c.getString(c.getColumnIndex(KEY_PLAYLISTS));
        			JSONArray pl = (JSONArray) new JSONTokener(s).nextValue();
        			for ( int i=0; i<pl.length(); i++ ) {
        				values.add(pl.getString(i));
        			}
        		}
        		catch(JSONException ex)
        		{
        			Log.e("SongDatabase", "playlist error");
        		}
        		c.moveToNext();
        	}
        }

        return new ArrayList<String>(values);
    }
    
}
