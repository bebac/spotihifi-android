package benny.bach.spotihifi;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

public class SpotifyService extends Thread implements Callback {
	private static final String TAG = "SpotifyService";
	
	public static final int SYNC_MSG_ID = 1;
	public static final int SYNC_COMPLETE_NO_CHANGE_MSG_ID = 2;
	public static final int SYNC_COMPLETE_RELOAD_MSG_ID = 21;
	public static final int PLAY_MSG_ID = 3;
	public static final int PAUSE_MSG_ID = 4;
	public static final int QUEUE_MSG_ID = 5;
	public static final int SKIP_MSG_ID = 6;
	public static final int STOP_MSG_ID = 7;
	public static final int RESULT_MSG_ID = 8;
	public static final int CONNECT_MSG_ID = 9;
	public static final int DISCONNECT_MSG_ID = 10;
	
	// Message tags.
	public static final String TAG_IP_ID = "ip";
	public static final String TAG_PORT_ID = "port";
	public static final String TAG_TRACK_ID = "track";
	public static final String TAG_RESULT_ID = "result";
	public static final String TAG_PLAYLIST_ID = "playlist";
	
	private SongDatabase mDb;
	private Handler mReqHandler;
	private Handler mResHandler;
	private Socket mSocket;
	private String mIpAddress;
	private int mPort;
	
	// TODO: Should probably be in SongDatabase.
	private long mIncarnation;
	private long mTransaction;
	
	public SpotifyService(Handler handler, SongDatabase db) 
	{
		mResHandler = handler;
		mDb = db;
		mIncarnation = -1;
		mTransaction = -1;
		
		HandlerThread handlerThread = new HandlerThread("SpotifyServiceHandler");
        handlerThread.start();
  
        mReqHandler = new Handler(handlerThread.getLooper(), this);
	}
	
	@Override
	public void run() 
	{
		try {
			while ( true ) {
				if ( mSocket != null && mSocket.isConnected() ) {
					try {
						InputStream in = mSocket.getInputStream();
						
						byte hbuf[] = new byte[4];
						
						int hl = 0;
						
						do {
							int received = in.read(hbuf, hl, 4-hl);
							if ( received > 0 ) {
								hl += received;
							}
						} while (hl < 4);
						
						int len = 0;
					
						len += (int)(hbuf[3]&0xff);
						len += (int)(hbuf[2]&0xff)<<8;
						len += (int)(hbuf[1]&0xff)<<16;
						len += (int)(hbuf[0]&0xff)<<24;
						
						byte bbuf[] = new byte[len];

						int bl = 0;
						
						do {
							int received = in.read(bbuf, bl, len-bl);
							if ( received > 0 ) {
								bl += received;
							}
						} while (bl < len);
						
						String body = new String(bbuf, "UTF-8");
						
						try {
							JSONObject object = (JSONObject) new JSONTokener(body).nextValue();
							
							// TODO: Check message type.
							
							int id = object.optInt("id");
							
							if ( id == 1 ) {
								syncResponseHandler(object);
							}
							else if ( id > 1 ) {
								Log.i(TAG, "result:" + object.toString());

								Message msg = mResHandler.obtainMessage(RESULT_MSG_ID);
								Bundle bundle = new Bundle();
								bundle.putString(TAG_RESULT_ID, object.optString("result"));
								msg.setData(bundle);
								mResHandler.sendMessage(msg);
							}
							else {
								Log.i(TAG, "result:" + object.toString());
							}
							
						}
						catch (JSONException ex) {
							Log.i(TAG, "spotify service json error");
						}	
					}
					catch(IOException ex) {
						Log.e(TAG, "io exception");
					}
				}
				else {
					Thread.sleep(1000);
				}
			}
		}
		catch(InterruptedException ex) {
			Log.i(TAG, "spotify service interrupted");
		}
	}

	@Override
	public boolean handleMessage(Message msg) 
	{
		switch ( msg.what )
		{
		case SYNC_MSG_ID:
			syncHandler();
			break;
		case QUEUE_MSG_ID:
		{
			Bundle bundle = msg.getData();
			String trackId = bundle.getString(TAG_TRACK_ID);
			queueHandler(trackId);
			break;
		}
		case PLAY_MSG_ID:
		{
			Bundle bundle = msg.getData();
			String playlist = bundle.getString(TAG_PLAYLIST_ID);
			playHandler(playlist);
			break;
		}
		case PAUSE_MSG_ID:
			pauseHandler();
			break;
		case SKIP_MSG_ID:
			skipHandler();
			break;
		case STOP_MSG_ID:
			stopHandler();
			break;
		case CONNECT_MSG_ID:
		{
			Bundle bundle = msg.getData();
			mIpAddress = bundle.getString(TAG_IP_ID);
			mPort = bundle.getInt(TAG_PORT_ID);
			connectHandler();
			break;
		}
		case DISCONNECT_MSG_ID:
			disconnectHandler();
			break;
		default:
			Log.i(TAG, "spotify service got unknown message");
			break;
		}
		return false;
	}

	public void sync()
	{
		Message msg = mReqHandler.obtainMessage(SYNC_MSG_ID);
		mReqHandler.sendMessage(msg);
	}

	public void playerPlay(String playlist)
	{
		Message msg = mReqHandler.obtainMessage(PLAY_MSG_ID);
		Bundle bundle = new Bundle();
		bundle.putString(TAG_PLAYLIST_ID, playlist);
		msg.setData(bundle);
		mReqHandler.sendMessage(msg);
	}

	public void playerPause()
	{
		Message msg = mReqHandler.obtainMessage(PAUSE_MSG_ID);
		mReqHandler.sendMessage(msg);
	}

	public void playerSkip()
	{
		Message msg = mReqHandler.obtainMessage(SKIP_MSG_ID);
		mReqHandler.sendMessage(msg);
	}
	
	public void playerStop()
	{
		Message msg = mReqHandler.obtainMessage(STOP_MSG_ID);
		mReqHandler.sendMessage(msg);
	}
	
	public void queue(String trackId)
	{
		Message msg = mReqHandler.obtainMessage(QUEUE_MSG_ID);
		Bundle bundle = new Bundle();
		bundle.putString(TAG_TRACK_ID, trackId);
		msg.setData(bundle);
		mReqHandler.sendMessage(msg);
	}	

	public void connect(String ip, int port)
	{
		Message msg = mReqHandler.obtainMessage(CONNECT_MSG_ID);
		Bundle bundle = new Bundle();
		bundle.putString(TAG_IP_ID, ip);
		bundle.putInt(TAG_PORT_ID, port);
		msg.setData(bundle);
		mReqHandler.sendMessage(msg);		
	}
	
	public void disconnect()
	{
		Message msg = mReqHandler.obtainMessage(DISCONNECT_MSG_ID);
		mReqHandler.sendMessage(msg);
	}
		
	private void syncHandler()
	{
		Log.i(TAG, "spotify service got sync message");
		
		if ( mSocket == null ) {
			connectHandler();
		}
		
		if ( !mSocket.isConnected() ) {
			Log.i(TAG, "spotify service connect failed");
			return;
		}
	
		try {			
			String msg = "{\"jsonrpc\":\"2.0\", \"method\":\"sync\",\"params\":{" +
					"\"incarnation\":\"" + Long.toString(mIncarnation, 10) + "\"," +
					"\"transaction\":\"" + Long.toString(mTransaction, 10) + "\"}," +
					"\"id\":1}"; 

			sendRequest(msg);
		}
		catch(IOException ex) {
			Log.i(TAG, "spotify service write failed");
		}
	}

	private void playHandler(String playlist)
	{
		Log.i(TAG, "spotify service got play message");
		
		if ( mSocket == null ) {
			connectHandler();
		}
		
		if ( !mSocket.isConnected() ) {
			Log.i(TAG, "spotify service connect failed");
			return;
		}
	
		try {			 
			String msg = "{\"jsonrpc\":\"2.0\", \"method\":\"play\",";
		    if ( playlist != null ) {
		    	msg += "\"params\":{\"playlist\":\"" + playlist + "\"},";
		    }
		    else {
		    	//msg += "\"params\":{\"playlist\":\"\"},";
		    	msg += "\"params\":[],";
		    }
		    msg += "\"id\":3}"; 

			sendRequest(msg);
		}
		catch(IOException ex) {
			Log.i(TAG, "spotify service write failed");
		}
	}

	private void pauseHandler()
	{
		Log.i(TAG, "spotify service got play message");
		
		if ( mSocket == null ) {
			connectHandler();
		}
		
		if ( !mSocket.isConnected() ) {
			Log.i(TAG, "spotify service connect failed");
			return;
		}
	
		try {			 
			String msg = "{\"jsonrpc\":\"2.0\", \"method\":\"pause\",\"params\":[],\"id\":4}"; 

			sendRequest(msg);
		}
		catch(IOException ex) {
			Log.i(TAG, "spotify service write failed");
		}
	}

	private void skipHandler()
	{
		Log.i(TAG, "spotify service got play message");
		
		if ( mSocket == null ) {
			connectHandler();
		}
		
		if ( !mSocket.isConnected() ) {
			Log.i(TAG, "spotify service connect failed");
			return;
		}
	
		try {			 
			String msg = "{\"jsonrpc\":\"2.0\", \"method\":\"skip\",\"params\":[],\"id\":6}"; 

			sendRequest(msg);
		}
		catch(IOException ex) {
			Log.i(TAG, "spotify service write failed");
		}
	}

	private void stopHandler()
	{
		Log.i(TAG, "spotify service got play message");
		
		if ( mSocket == null ) {
			connectHandler();
		}
		
		if ( !mSocket.isConnected() ) {
			Log.i(TAG, "spotify service connect failed");
			return;
		}
	
		try {			 
			String msg = "{\"jsonrpc\":\"2.0\", \"method\":\"stop\",\"params\":[],\"id\":7}"; 

			sendRequest(msg);
		}
		catch(IOException ex) {
			Log.i(TAG, "spotify service write failed");
		}
	}
	
	private void queueHandler(String trackId)
	{
		Log.i(TAG, "spotify service got play message");
		
		if ( mSocket == null ) {
			connectHandler();
		}
		
		if ( !mSocket.isConnected() ) {
			Log.i(TAG, "spotify service connect failed");
			return;
		}
	
		try {			
			String params = "\"spotify:track:" + trackId + "\""; 
			String msg = "{\"jsonrpc\":\"2.0\", \"method\":\"queue\",\"params\":[" + params + "],\"id\":2}"; 

			sendRequest(msg);
		}
		catch(IOException ex) {
			Log.i(TAG, "spotify service write failed");
		}
	}

	public void connectHandler()
	{
		try {
			mSocket = new Socket();
			mSocket.connect(new InetSocketAddress(mIpAddress, mPort));
		}
		catch(IOException ex) {
			Log.e(TAG, "socket connect failed");
		}
	}
	
	public void disconnectHandler() 
	{
		try 
		{
			if ( mSocket.isConnected() ) {
				mSocket.close();
				mSocket = null;
			}
		}
		catch(IOException ex) {
			Log.e(TAG, "socket close failed");
		}
	}

	private void syncResponseHandler(JSONObject json) 
	{
		JSONObject result = json.optJSONObject("result");
		
		if ( result != null )
		{
			long incarnation = Long.parseLong(result.optString("incarnation", "-1"));
			long transaction = Long.parseLong(result.optString("transaction", "-1"));
			
			if ( incarnation == mIncarnation )
			{
				// TODO: Handle transaction. For now assume nothing has changed.
				Log.i(TAG, "sync result - nothing changed");
				Message msg = mResHandler.obtainMessage(SYNC_COMPLETE_NO_CHANGE_MSG_ID);
				mResHandler.sendMessage(msg);			
			}
			else 
			{
				// Rebuild database.
				JSONArray tracks = result.optJSONArray("tracks");
				if ( tracks != null ) 
				{
					for ( int i=0; i<tracks.length(); i++ ) 
					{
						JSONObject song = tracks.optJSONObject(i);
						if ( song != null ) {
							try 
							{
								String title = song.getString("title");
								String artist = song.getString("artist");
								String album = song.getString("album");
								int track_number = song.getInt("track_number");
								String track_id = song.getString("track_id");
								JSONArray playlists = song.getJSONArray("playlists");
								
								mDb.insertSong(title, artist, album, track_number, track_id, playlists.toString());
							}
							catch(JSONException ex) {
								Log.e(TAG, "invalid song");
							}
						}
						else {
							Log.e(TAG, "song is null");
						}
					}
				}
				else {
					Log.e(TAG, "sync result has no tracks");
				}
				
				mIncarnation = incarnation;
				mTransaction = transaction;
				
				Message msg = mResHandler.obtainMessage(SYNC_COMPLETE_RELOAD_MSG_ID);
				mResHandler.sendMessage(msg);							
			}			
		}
		else {
			Log.e(TAG, "sync result == null");
		}		
	}
	
	private void sendRequest(String msg) throws IOException
	{
		BufferedOutputStream out = new BufferedOutputStream(mSocket.getOutputStream());
		
		byte[] buf = msg.getBytes("UTF-8");
		
		int len = buf.length;
		
		out.write((byte)(len>>>24));
		out.write((byte)(len>>>16));
		out.write((byte)(len>>>8));
		out.write((byte)(len));
		out.write(buf, 0, len);
		out.flush();
	}
}
