package benny.bach.spotihifi;

import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONTokener;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class SpotiHifiProvider extends ContentProvider 
{
	private static final String TAG = "SpotiHifiProvider";

	/////
	// Database constants.
	//
    private static final String DATABASE_NAME = null;
    private static final int DATABASE_VERSION = 1;
	
	/////
	// URI matcher action constants.
	//
	private static final int TRACKS = 1;
	private static final int TRACK_ID = 2;
	private static final int PLAYLISTS = 3;
	private static final int ARTISTS = 4;
	private static final int SERVICE_UNAVAILABLE = 5;
	
	/////
	
	private static final UriMatcher sUriMatcher;

	private DatabaseHelper mOpenHelper;
	
    private SpotiHifiService mService;
    boolean mBound = false;
	
	static
	{
		/////
		// Initialize URI matcher.
		//
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		
		sUriMatcher.addURI(SpotiHifi.AUTHORITY, "tracks", TRACKS);
		sUriMatcher.addURI(SpotiHifi.AUTHORITY, "tracks/#", TRACK_ID);
		sUriMatcher.addURI(SpotiHifi.AUTHORITY, "playlists", PLAYLISTS);
		sUriMatcher.addURI(SpotiHifi.AUTHORITY, "artists", ARTISTS);
		
		/////
    }
	
	// Projection for building playlist list.
	private static final String[] READ_PLAYLISTS_PROJECTION = new String[] {
		SpotiHifi.Tracks.COLUMN_NAME_PLAYLISTS
	};

	// Projection for building artist list.
	private static final String[] READ_ARTISTS_PROJECTION = new String[] {
		SpotiHifi.Tracks.COLUMN_NAME_ARTIST
	};	
	
	/////
	// Database helper.
	//
	static class DatabaseHelper extends SQLiteOpenHelper
	{
		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) 
		{
			db.execSQL("CREATE TABLE " + SpotiHifi.Tracks.TABLE_NAME + " (" 
			        + SpotiHifi.Tracks.COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
			        + SpotiHifi.Tracks.COLUMN_NAME_TITLE + ","
			        + SpotiHifi.Tracks.COLUMN_NAME_ARTIST + ","
			        + SpotiHifi.Tracks.COLUMN_NAME_ALBUM + ","
			        + SpotiHifi.Tracks.COLUMN_NAME_TRACK_NUMBER + ","
			        + SpotiHifi.Tracks.COLUMN_NAME_TRACK_ID + " UNIQUE,"
			        + SpotiHifi.Tracks.COLUMN_NAME_PLAYLISTS + ");");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) 
		{
			// Logs that the database is being upgraded
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
					+ newVersion + ", which will destroy all old data");

			// Kills the table and existing data
			db.execSQL("DROP TABLE IF EXISTS " + SpotiHifi.Tracks.TABLE_NAME);

			// Recreates the database with a new version
			onCreate(db);
        }
    }
	
	/////
	// SpotiHifiService connection.
	//

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) 
        {
        	// Get SpotiHifiService reference.
            SpotiHifiService.SpotiHifiBinder binder = (SpotiHifiService.SpotiHifiBinder)service;
            mService = (SpotiHifiService)binder.getService();
            
            if ( mService.isConnected() ) {
            	// Issue sync request.
            	//mService.sync(-1, -1);
            	Log.e(TAG, "Should not happen!");
            }
            else {
            	mService.connect();
            }
            
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };	
	
    /////
    
	@Override
	public boolean onCreate() 
	{
		Log.i(TAG, "create spotihifi provider");
		
		mOpenHelper = new DatabaseHelper(getContext());

		Intent intent = new Intent(getContext(), SpotiHifiService.class);
		//getContext().startService(intent);
    	getContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    	
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) 
	{
        switch (sUriMatcher.match(uri)) 
        {
        case 1:
        {
        	Log.i(TAG, "query tracks");
        	
        	SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        	
        	Cursor c = db.query(
        			SpotiHifi.Tracks.TABLE_NAME, 
        			projection, 
        			selection, 
        			null, 
        			null, 
        			null, 
        			SpotiHifi.Tracks.COLUMN_NAME_ARTIST + "," + SpotiHifi.Tracks.COLUMN_NAME_ALBUM + "," + SpotiHifi.Tracks.COLUMN_NAME_TRACK_NUMBER
        		);
        	
        	c.setNotificationUri(getContext().getContentResolver(), uri);
        	
        	return c;
        }
        case 2:
        {
        	SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        	
        	Cursor c = db.query(
        			SpotiHifi.Tracks.TABLE_NAME, 
        			projection, 
        			SpotiHifi.Tracks.COLUMN_NAME_ID + "=" + uri.getPathSegments().get(SpotiHifi.Tracks.ID_PATH_POSITION), 
        			null, 
        			null, 
        			null, 
        			null
        		);
        	
        	c.setNotificationUri(getContext().getContentResolver(), uri);
        	
        	return c;        	
        }
        case 3:
        {
        	SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        	
        	Cursor c_ = db.query(
        			SpotiHifi.Tracks.TABLE_NAME, 
        			READ_PLAYLISTS_PROJECTION, 
        			null, 
        			null, 
        			null, 
        			null, 
        			null
        		);

            Set<String> values = new HashSet<String>();
            
            if (c_ == null || !c_.moveToFirst()) {
            	Log.i("kfjdk", "hmmm no playlists");
            }
            else {
            	while(!c_.isAfterLast()) 
            	{
            		try {
            			String s = c_.getString(c_.getColumnIndex(SpotiHifi.Tracks.COLUMN_NAME_PLAYLISTS));
            			JSONArray pl = (JSONArray) new JSONTokener(s).nextValue();
            			for ( int i=0; i<pl.length(); i++ ) {
            				values.add(pl.getString(i));
            			}
            		}
            		catch(JSONException ex)
            		{
            			Log.e("SongDatabase", "playlist error");
            		}
            		c_.moveToNext();
            	}
            }

            MatrixCursor c = new MatrixCursor(SpotiHifi.Playlists.PLAYLIST_PROJECTION, values.size());
        	
            Object[] array = values.toArray();
            for(int i=0; i<array.length; i++) {
               c.addRow(new Object[] { i, array[i] }); 
            }
            
        	c.setNotificationUri(getContext().getContentResolver(), uri);
        	
        	return c;        	
        }
        case 4:
        {
            SQLiteDatabase db = mOpenHelper.getReadableDatabase();

        	Cursor c_ = db.query(
        			true,
        			SpotiHifi.Tracks.TABLE_NAME, 
        			READ_ARTISTS_PROJECTION, 
        			null,
        			null, 
        			null, 
        			null, 
        			SpotiHifi.Tracks.COLUMN_NAME_ARTIST,
        			null
        		);

            MatrixCursor c = new MatrixCursor(SpotiHifi.Artists.ARTIST_PROJECTION);
            
            int i = 0;
            if (c_ == null || !c_.moveToFirst()) {
            	Log.i("kfjdk", "hmmm not artists");
            }
            else {
            	while(!c_.isAfterLast()) {
            		c.addRow(new Object[] { i++, c_.getString(c_.getColumnIndex(SpotiHifi.Tracks.COLUMN_NAME_ARTIST)) });
            		c_.moveToNext();
            	}
            }
                    
            c.setNotificationUri(getContext().getContentResolver(), uri);
            
            return c;
        }
        default:
            // If the URI is not recognized, you should do some error handling here.
        	Log.e(TAG, "unrecognized uri");
        	return null;
        }
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) 
	{
        if (sUriMatcher.match(uri) != TRACKS) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // TODO: Validate values.
        
    	SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    	 
    	db.insert(SpotiHifi.Tracks.TABLE_NAME, null, values);

		return null;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Bundle call(String method, String arg, Bundle extras) 
	{
		if ( method == "connected" ) 
		{
			Toast.makeText(getContext(), "synchronizing...", Toast.LENGTH_SHORT).show();
			
			mService.sync(-1, -1);
		}
		else if ( method == "disconnected") 
		{
			Toast.makeText(getContext(), "connection lost!", Toast.LENGTH_SHORT).show();
			
			SQLiteDatabase db = mOpenHelper.getWritableDatabase();
			
			db.execSQL("DELETE FROM " + SpotiHifi.Tracks.TABLE_NAME);
			
			getContext().getContentResolver().notifyChange(SpotiHifi.Tracks.CONTENT_URI, null);
			getContext().getContentResolver().notifyChange(SpotiHifi.Playlists.CONTENT_URI, null);
			getContext().getContentResolver().notifyChange(SpotiHifi.Artists.CONTENT_URI, null);
		}
		return null;
    }
		
}
