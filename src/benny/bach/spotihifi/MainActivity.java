package benny.bach.spotihifi;

import java.util.ArrayList;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class MainActivity extends Activity implements Callback {
	private static final String TAG = "MainActivity";
	
	private SongDatabase mDb;
	private SpotifyService mSpotify;
	private TrackListFragment mTrackListFragment;
	private ArtistListFragment mArtistListFragment;
	private PlaylistListFragment mPlaylistListFragment;
	//private ViewPager mViewPager;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mDb = new SongDatabase(this);
		mSpotify = new SpotifyService(new Handler(this), mDb);
		mTrackListFragment = new TrackListFragment();
		mArtistListFragment = new ArtistListFragment();
		mPlaylistListFragment = new PlaylistListFragment();
		
		mSpotify.start();
		//mSpotify.sync();
		Log.i(TAG, "created");
	}

	@Override
	public void onPause() {
	    super.onPause();
	    Log.i(TAG, "pausing");
	    mSpotify.disconnect();
	}	
	
	@Override
	public void onResume() {
	    super.onResume();
	    Log.i(TAG, "resuming");

	    // Hmmm - For now remove any content as sync will load everything. Really need
	    // to show a loading message.
	    
	    Toast.makeText(getApplicationContext(), "synchronizing...", Toast.LENGTH_SHORT).show();
	    
	    clearBackStack();

	    getFragmentManager().beginTransaction()
        	.remove(mTrackListFragment)
        	.commit();

	    getFragmentManager().beginTransaction()
    		.remove(mArtistListFragment)
    		.commit();

	    getFragmentManager().beginTransaction()
			.remove(mPlaylistListFragment)
			.commit(); 
	   
	    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
	    
	    String ip = sp.getString(SettingsActivity.SERVER_IP, "");
	    String port = sp.getString(SettingsActivity.SERVER_PORT, "8081");
	    
	    mSpotify.connect(ip, Integer.parseInt(port));
	    mSpotify.sync();
	}	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
        case android.R.id.home:
        	Log.i(TAG, "go home");
            return true;
        case R.id.action_play:
        	mSpotify.playerPlay(null);
        	return true;
        case R.id.action_play_all:
        	mSpotify.playerPlay("");
        	return true;
        case R.id.action_pause:
        	mSpotify.playerPause();
        	return true;
        case R.id.action_skip:
        	mSpotify.playerSkip();
        	return true;
        case R.id.action_stop:
        	mSpotify.playerStop();
        	return true;
        case R.id.action_songs:
        	if ( !mTrackListFragment.isVisible() )
        	{	
        		Log.i(TAG, "Show songs");
        		clearBackStack();
		        getFragmentManager().beginTransaction()
		                .replace(android.R.id.content, mTrackListFragment)
		                .commit();
        	}
        	return true;
        case R.id.action_artists:
        	if ( !mArtistListFragment.isVisible() )
        	{	
        		Log.i(TAG, "Show artists");
        		clearBackStack();
		        getFragmentManager().beginTransaction()
		                .replace(android.R.id.content, mArtistListFragment)
		                .commit();
        	}
        	return true;
        case R.id.action_playlists:
        	if ( !mPlaylistListFragment.isVisible() )
        	{	
        		Log.i(TAG, "Show playlists");
        		clearBackStack();
		        getFragmentManager().beginTransaction()
		                .replace(android.R.id.content, mPlaylistListFragment)
		                .commit();
        	}
        	return true;
        case R.id.action_settings:
            Intent settings = new Intent(this, SettingsActivity.class);
            //startActivityForResult(i, RESULT_SETTINGS);
            startActivity(settings);
            return true;
        default:
        	Log.i(TAG, "go " + item.getItemId());
            return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	public boolean handleMessage(Message msg) {
		switch ( msg.what )
		{
		case SpotifyService.SYNC_COMPLETE_RELOAD_MSG_ID:
			// Hmmm - For now create a new track list fragment and show it.
			mTrackListFragment = new TrackListFragment();
			// FALL THROUGH INTENDED
		case SpotifyService.SYNC_COMPLETE_NO_CHANGE_MSG_ID:
			Log.i(TAG, "Show songs");
	        getFragmentManager().beginTransaction()
	                .replace(android.R.id.content, mTrackListFragment)
	                .commit();
			break;
		case SpotifyService.RESULT_MSG_ID:
			Bundle bundle = msg.getData();
			String result = bundle.getString(SpotifyService.TAG_RESULT_ID);
			Toast.makeText(getApplicationContext(), result, Toast.LENGTH_SHORT).show();
			break;
		default:
			Log.i(TAG, "main activity got unknown message");
			break;
		}
		return false;
	}
	
	private void showSongsForArtist(String artist)
	{
		TrackListFragment f = new TrackListFragment();
		
		Bundle args = new Bundle();
        args.putString("where", "artist=\""+artist+"\"");
        f.setArguments(args);
        
        getFragmentManager().beginTransaction()
				.replace(android.R.id.content, f)
				.addToBackStack("artist.tracklist")
				.commit();
	}

	private void showSongsForPlaylist(String playlist)
	{
		TrackListFragment f = new TrackListFragment();
		
		Bundle args = new Bundle();
		// TODO: This probably doesn't handle all escaping, have to study the specs.
		playlist = playlist.replaceAll("/", "\\\\/");
		playlist = playlist.replaceAll("'", "''");
		args.putString("where", "playlists LIKE '%\""+playlist+"\"%'");
        f.setArguments(args);
        
        getFragmentManager().beginTransaction()
				.replace(android.R.id.content, f)
				.addToBackStack("playlist.tracklist")
				.commit();
	}
	
	private SongDatabase getDatabase() {
		return mDb;
	}
	
	private void playSong(String trackId) {
		mSpotify.queue(trackId);
	}

	private void playPlaylist(String playlist) {
		mSpotify.playerPlay(playlist);
	}
	
    private void clearBackStack()
    {
    	for ( int i=0; i<getFragmentManager().getBackStackEntryCount(); ++i) {
    		getFragmentManager().popBackStack();
    	}
    }
	
	public static class TrackListFragment extends Fragment
	{
		private ListView mListView;
		private String mWhere;
		
		public TrackListFragment() {
		}
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) 
		{
			View rootView = inflater.inflate(R.layout.fragment_tracks, container, false);
				        
	        Bundle args = getArguments();
	        
	        mListView = (ListView)rootView.findViewById(R.id.tracklist);
	        mWhere = (args == null ? null : args.getString("where"));
			
	        update();
	        
			return rootView;
		}
		
		@Override
	    public void onAttach(Activity activity) {
			super.onAttach(activity);
			Log.i(TAG, "TrackListFragment: attach");
		}

		@Override
	    public void onDetach() {
			super.onDetach();
			Log.i(TAG, "TrackListFragment: detach");
		}
		
		@Override
		public void onResume()
		{
			super.onResume();
			Log.i(TAG, "TrackListFragment: resume");
		}

		@Override
		public void onPause()
		{
			super.onPause();
			Log.i(TAG, "TrackListFragment: pause");
		}		
		
		@Override
		public void onDestroyView()
		{
			super.onDestroyView();
			Log.i(TAG, "TrackListFragment: destroy view");
		}

		@Override
		public void onDestroy()
		{
			super.onDestroy();
			Log.i(TAG, "TrackListFragment: destroy");
		}
		
		public void update()
		{
			MainActivity a = ((MainActivity)getActivity());
			// Get database reference.
			//final SongDatabase db = ((MainActivity)getActivity()).getDatabase();
			final SongDatabase db = a.getDatabase();
			
			String[] from = { "title", "artist" };
	        int[] to = { R.id.song_title, R.id.song_artist };

			Cursor cursor = db.querySongs(mWhere);
			
			SimpleCursorAdapter cursorAdapter = new SimpleCursorAdapter(getActivity(), R.layout.song, cursor, from, to);

			mListView.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> arg0, View v, int position, long id)
				{
					Cursor c = db.querySongById(id);
				
					if (c == null || !c.moveToFirst()) {
						Log.i(TAG, "song not found " + id);
					}
					else {
						int trackIdIndex = c.getColumnIndex("track_id");
						String trackId = c.getString(trackIdIndex);
						((MainActivity)getActivity()).playSong(trackId);
					}
				}
			});			
			
			mListView.setAdapter(cursorAdapter);			
		}
	}	
	
	public static class ArtistListFragment extends Fragment
	{	
		public ArtistListFragment() {
		}
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) 
		{
			View rootView = inflater.inflate(R.layout.fragment_artists, container, false);

			// Get database reference.
			final SongDatabase db = ((MainActivity)getActivity()).getDatabase();
			
	        final ArrayList<String> values = db.queryArtistsAll();

			ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), 
					R.layout.song, R.id.song_title, values);
			
			ListView lv = (ListView)rootView.findViewById(R.id.artistlist);

			lv.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> arg0, View v, int position, long id)
				{
					Log.i(TAG, "artist clicked " + values.get(position));
					((MainActivity)getActivity()).showSongsForArtist(values.get(position));
				}
			});					
			
			lv.setAdapter(adapter);
			
			return rootView;
		}
	}
	
	public static class PlaylistListFragment extends Fragment
	{	
		private MainActivity mActivity;
		
		public PlaylistListFragment() {
		}
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) 
		{
			mActivity = ((MainActivity)getActivity());
			
			View rootView = inflater.inflate(R.layout.fragment_playlists, container, false);

			// Get database reference.
			//final SongDatabase db = ((MainActivity)getActivity()).getDatabase();
			final SongDatabase db = mActivity.getDatabase();
			
	        final ArrayList<String> values = db.queryPlaylistsAll();

			ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), 
					R.layout.song, R.id.song_title, values);
			
			ListView lv = (ListView)rootView.findViewById(R.id.playlist);

			lv.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> arg0, View v, int position, long id)
				{
					Log.i(TAG, "playlist clicked " + values.get(position));
					//((MainActivity)getActivity()).showSongsForPlaylist(values.get(position));
					mActivity.showSongsForPlaylist(values.get(position));
				}
			});
			
			registerForContextMenu(lv);
			
			lv.setAdapter(adapter);
			
			return rootView;
		}
		
		@Override
		public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) 
		{
		    super.onCreateContextMenu(menu, v, menuInfo);
		    MenuInflater inflater = mActivity.getMenuInflater();
		    inflater.inflate(R.menu.playlist_ctx_menu, menu);
		}
		
		@Override
		public boolean onContextItemSelected(MenuItem item) 
		{
		    AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
		 
		    ListView lv = (ListView)getView().findViewById(R.id.playlist);
		    
		    switch (item.getItemId()) 
		    {
		    case R.id.play_item:
		    	//Toast.makeText(mActivity.getApplicationContext(), lv.getItemAtPosition(info.position).toString(), Toast.LENGTH_SHORT).show();
		    	mActivity.playPlaylist(lv.getItemAtPosition(info.position).toString());
		        return true;
		    }
		    return false;
		}		
	}
}
