package benny.bach.spotihifi;

import android.net.Uri;
import android.provider.BaseColumns;

public final class SpotiHifi 
{
	public static final String AUTHORITY = "benny.bach.spotihifi";
	
	// Prevent instantiation.
	private SpotiHifi()
	{
	}
	
	/////
	// Message IDs
	//
	public static final int SYNC_MSG_ID = 1;
	public static final int PLAY_MSG_ID = 2;
	public static final int QUEUE_MSG_ID = 3;
	public static final int STOP_MSG_ID = 4;
	public static final int PAUSE_MSG_ID = 5;
	public static final int SKIP_MSG_ID = 6;
	public static final int RESULT_MSG_ID = 7;
	public static final int SERVICE_CONNECT_MSG_ID = 8;
	
	/////
	// Track table contract.
	//
	
	public static final class Tracks implements BaseColumns 
	{
		// Prevent instantiation.
		private Tracks() {}
		
		// Table name.
		public static final String TABLE_NAME = "tracks";
		
		// Tracks table column names.
		public static final String COLUMN_NAME_ID = "_id";
		public static final String COLUMN_NAME_TITLE = "title";
		public static final String COLUMN_NAME_ARTIST = "artist";
		public static final String COLUMN_NAME_ALBUM = "album";
		public static final String COLUMN_NAME_TRACK_NUMBER = "track_number";
		public static final String COLUMN_NAME_TRACK_ID = "track_id";
		public static final String COLUMN_NAME_PLAYLISTS = "playlists";
		
		// All track columns projection.
		public static final String[] TRACK_PROJECTION = {
			COLUMN_NAME_ID,
			COLUMN_NAME_TITLE,
			COLUMN_NAME_ARTIST,
			COLUMN_NAME_ALBUM,
			COLUMN_NAME_TRACK_NUMBER,
			COLUMN_NAME_TRACK_ID,
			COLUMN_NAME_PLAYLISTS
		};
		
		/////
		// URIs
		//

		// Parts.
		private static final String SCHEME = "content://";
		private static final String PATH_TRACKS = "/tracks";
		private static final String PATH_TRACKS_ID = "/tracks/";

		public static final int ID_PATH_POSITION = 1;
		
		public static final Uri CONTENT_URI =  Uri.parse(SCHEME + AUTHORITY + PATH_TRACKS);
		public static final Uri CONTENT_URI_BASE =  Uri.parse(SCHEME + AUTHORITY + PATH_TRACKS_ID);
	}
	
	/////
	// Playlist table contract.
	//
	
	public static final class Playlists implements BaseColumns
	{
		// Prevent instantiation.
		private Playlists() {}
		
		// Table name.
		public static final String TABLE_NAME = "playlists";
		
		// Playlist table column names.
		public static final String COLUMN_NAME_ID = "_id";
		public static final String COLUMN_NAME_TITLE = "title";

		// All playlist columns projection.
		public static final String[] PLAYLIST_PROJECTION = {
			COLUMN_NAME_ID,
			COLUMN_NAME_TITLE
		};

		/////
		// URIs
		//

		// Parts.
		private static final String SCHEME = "content://";
		private static final String PATH_PLAYLISTS = "/playlists";

		public static final int ID_PATH_POSITION = 1;
		
		public static final Uri CONTENT_URI =  Uri.parse(SCHEME + AUTHORITY + PATH_PLAYLISTS);
	}

	/////
	// Artist table contract.
	//
	
	public static final class Artists implements BaseColumns
	{
		// Prevent instantiation.
		private Artists() {}
		
		// Table name.
		public static final String TABLE_NAME = "artists";
		
		// Artist table column names.
		public static final String COLUMN_NAME_ID = "_id";
		public static final String COLUMN_NAME_ARTIST = "artist";

		// All artist columns projection.
		public static final String[] ARTIST_PROJECTION = {
			COLUMN_NAME_ID,
			COLUMN_NAME_ARTIST
		};

		/////
		// URIs
		//

		// Parts.
		private static final String SCHEME = "content://";
		private static final String PATH_ARTISTS = "/artists";

		public static final int ID_PATH_POSITION = 1;
		
		public static final Uri CONTENT_URI =  Uri.parse(SCHEME + AUTHORITY + PATH_ARTISTS);
	}
	
}
