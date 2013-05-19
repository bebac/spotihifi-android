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

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class SpotiHifiService extends Service implements OnSharedPreferenceChangeListener
{
	private static final String TAG = "SpotiHifiService";

	private static final String TAG_TRACK_ID = "track_id";
	private static final String TAG_PLAYLIST = "playlist";

	private final IBinder mBinder = new SpotiHifiBinder();

	private Looper mServiceLooper;
	private ServiceHandler mServiceHandler;
	private Handler mResultHandler;
	private SocketConnection mConn;

    //int mStartMode;
    //IBinder mBinder;
    //boolean mAllowRebind;

    @Override
    public void onCreate()
    {
    	Log.i(TAG, "creating spotihifi service");

    	HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.registerOnSharedPreferenceChangeListener(this);

	    mResultHandler = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
    	Log.i(TAG, "spotihifi service start command");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
    	Log.i(TAG, "spotihifi service bind");
        return mBinder;
    }

    //@Override
    //public boolean onUnbind(Intent intent)
    //{
    //	Log.i(TAG, "spotihifi service unbind");
    //    return mAllowRebind;
    //}

    //@Override
    //public void onRebind(Intent intent)
    //{
    //	Log.i(TAG, "spotihifi service unbind");
    //}

    @Override
    public void onDestroy()
    {
    	Log.i(TAG, "spotihifi service destroy");
    }

    public class SpotiHifiBinder extends Binder
    {
        SpotiHifiService getService() {
          return SpotiHifiService.this;
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sp, String key)
    {
    	Log.i(TAG, "pref changed " + key);

    	if ( key.equals("pref_server_ip") || key.equals("pref_server_port") ) {
    		if ( isConnected() ) {
    			disconnectHandler();
    		}
    		connect();
    	}
    }

    synchronized public void setResultHandler(Handler handler)
    {
    	mResultHandler = handler;
    }
    
    public boolean isConnected()
    {
    	if ( mConn != null ) {
    		return mConn.isConnected();
    	}
    	else {
    		return false;
    	}
    }

	public void connect()
	{
		Message msg = mServiceHandler.obtainMessage(SpotiHifi.SERVICE_CONNECT_MSG_ID);
		mServiceHandler.sendMessage(msg);
	}    
    
    public void sync(long incarnation, long transaction)
    {
    	//Log.i(TAG, "spotihifi service sync, incarnation=" + incarnation + ", transaction=" + transaction);

		Message msg = mServiceHandler.obtainMessage(SpotiHifi.SYNC_MSG_ID);
		mServiceHandler.sendMessage(msg);
    }

	public void playerPlay(String playlist)
	{
		Message msg = mServiceHandler.obtainMessage(SpotiHifi.PLAY_MSG_ID);
		Bundle bundle = new Bundle();
		bundle.putString(TAG_PLAYLIST, playlist);
		msg.setData(bundle);
		mServiceHandler.sendMessage(msg);
	}
    
	public void queue(String trackId)
	{
		Message msg = mServiceHandler.obtainMessage(SpotiHifi.QUEUE_MSG_ID);
		Bundle bundle = new Bundle();
		bundle.putString(TAG_TRACK_ID, trackId);
		msg.setData(bundle);
		mServiceHandler.sendMessage(msg);
	}

	public void playerStop()
	{
		Message msg = mServiceHandler.obtainMessage(SpotiHifi.STOP_MSG_ID);
		mServiceHandler.sendMessage(msg);
	}

	public void playerPause()
	{
		Message msg = mServiceHandler.obtainMessage(SpotiHifi.PAUSE_MSG_ID);
		mServiceHandler.sendMessage(msg);
	}

	public void playerSkip()
	{
		Message msg = mServiceHandler.obtainMessage(SpotiHifi.SKIP_MSG_ID);
		mServiceHandler.sendMessage(msg);
	}
	
    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler
    {
        public ServiceHandler(Looper looper)
        {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg)
        {
    		switch ( msg.what )
    		{
    		case SpotiHifi.SERVICE_CONNECT_MSG_ID:
    			connectHandler();
    			break;
    		case SpotiHifi.SYNC_MSG_ID:
    			syncRequestHandler();
    			break;
    		case SpotiHifi.PLAY_MSG_ID:
    		{
    			Bundle bundle = msg.getData();
    			String playlist = bundle.getString(TAG_PLAYLIST);
    			playHandler(playlist);
    			break;
    		}
    		case SpotiHifi.QUEUE_MSG_ID:
    		{
    			Bundle bundle = msg.getData();
    			String trackId = bundle.getString(TAG_TRACK_ID);
    			queueRequestHandler(trackId);
    			break;
    		}
    		case SpotiHifi.STOP_MSG_ID:
    			playerStopRequestHandler();
    			break;
    		case SpotiHifi.PAUSE_MSG_ID:
    			playerPauseRequestHandler();
    			break;
    		case SpotiHifi.SKIP_MSG_ID:
    			playerSkipRequestHandler();
    			break;
    		default:
    			Log.e(TAG, "spotihifi service - unknown message id");
    			break;
    		}
        }
    }

    private void syncRequestHandler()
    {
		try
		{
			String rawMsg = "{\"jsonrpc\":\"2.0\", \"method\":\"sync\",\"params\":{" +
					"\"incarnation\":\"" + Long.toString(-1, 10) + "\"," +
					"\"transaction\":\"" + Long.toString(-1, 10) + "\"}," +
					"\"id\":" + SpotiHifi.SYNC_MSG_ID + "}";

			mConn.sendMessage(rawMsg);
		}
		catch(IOException ex) {
			//Toast.makeText(this, "sync request error!", Toast.LENGTH_SHORT).show();
			Log.e(TAG, "sync request error!");
			disconnectHandler();
		}
    }

	private void playHandler(String playlist)
	{	
		try {			 
			String msg = "{\"jsonrpc\":\"2.0\", \"method\":\"play\",";
		    if ( playlist != null ) {
		    	msg += "\"params\":{\"playlist\":\"" + playlist + "\"},";
		    }
		    else {
		    	msg += "\"params\":[],";
		    }
		    msg += "\"id\":" + SpotiHifi.PLAY_MSG_ID + "}"; 

		    Log.i(TAG, msg);
		    
			mConn.sendMessage(msg);
		}
		catch(IOException ex) {
			Log.e(TAG, "player play playlist=" + playlist + " request error!");
			disconnectHandler();
		}
	}
    
	private void queueRequestHandler(String trackId)
	{
		try
		{
			String params = "\"spotify:track:" + trackId + "\"";
			String msg = "{\"jsonrpc\":\"2.0\", \"method\":\"queue\",\"params\":[" + params + "],\"id\":" + SpotiHifi.QUEUE_MSG_ID + "}";

			mConn.sendMessage(msg);
		}
		catch(IOException ex) {
			//Toast.makeText(this, "queue request error!", Toast.LENGTH_SHORT).show();
			Log.e(TAG, "queue request error!");
			disconnectHandler();
		}
	}

	private void playerStopRequestHandler()
	{
		try {
			String msg = "{\"jsonrpc\":\"2.0\", \"method\":\"stop\",\"params\":[],\"id\":" + SpotiHifi.STOP_MSG_ID + "}";

			mConn.sendMessage(msg);
		}
		catch(IOException ex) {
			Log.e(TAG, "player stop request error!");
			disconnectHandler();
		}
	}

	private void playerPauseRequestHandler()
	{	
		try {			 
			String msg = "{\"jsonrpc\":\"2.0\", \"method\":\"pause\",\"params\":[],\"id\":" + SpotiHifi.PAUSE_MSG_ID + "}"; 

			mConn.sendMessage(msg);
		}
		catch(IOException ex) {
			Log.e(TAG, "player pause request error!");
			disconnectHandler();
		}
	}
	
	private void playerSkipRequestHandler()
	{	
		try {			 
			String msg = "{\"jsonrpc\":\"2.0\", \"method\":\"skip\",\"params\":[],\"id\":" + SpotiHifi.SKIP_MSG_ID + "}"; 

			mConn.sendMessage(msg);
		}
		catch(IOException ex) {
			Log.e(TAG, "player skip request error!");
			disconnectHandler();
		}
	}
	
	private void connectHandler()
	{
		if ( isConnected() ) {
			return;
		}

		try
		{
	        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

		    String ip = sp.getString(SettingsActivity.SERVER_IP, "");
		    String port = sp.getString(SettingsActivity.SERVER_PORT, "8081");

	        mConn = new SocketConnection(ip, Integer.parseInt(port));
			mConn.connect();

			getContentResolver().call(Uri.parse("content://benny.bach.spotihifi/service/state"), "connected", null, null);
		}
		catch(IOException ex)
		{
			Log.e(TAG, "connect failed!");
		}

	}
	
	private void disconnectHandler()
	{
		Log.i(TAG, "connection lost!");
		
		getContentResolver().call(Uri.parse("content://benny.bach.spotihifi/service/state"), "disconnected", null, null);
		
		mConn.close();
		mConn = null;
	}
	
    private void syncResponseHandler(JSONObject object)
    {
    	JSONObject result = object.optJSONObject("result");

		if ( result != null )
		{
			long incarnation = Long.parseLong(result.optString("incarnation", "-1"));
			long transaction = Long.parseLong(result.optString("transaction", "-1"));

			//if ( incarnation == mIncarnation )
			if ( false )
			{
				// TODO: Handle transaction. For now assume nothing has changed.
				Log.i(TAG, "sync result - nothing changed");
				//Message msg = mResHandler.obtainMessage(SYNC_COMPLETE_NO_CHANGE_MSG_ID);
				//mResHandler.sendMessage(msg);
			}
			else
			{
				// Rebuild database.
				JSONArray tracks = result.optJSONArray("tracks");
				if ( tracks != null )
				{
					// Create array to pass to bulk insert.
					ContentValues[] values = new ContentValues[tracks.length()];

					for ( int i=0; i<tracks.length(); i++ )
					{
						JSONObject song = tracks.optJSONObject(i);
						if ( song != null ) {
							try
							{
					        	ContentValues initialValues = new ContentValues();
					        	initialValues.put(SpotiHifi.Tracks.COLUMN_NAME_TITLE, song.getString("title"));
					        	initialValues.put(SpotiHifi.Tracks.COLUMN_NAME_ARTIST, song.getString("artist"));
					        	initialValues.put(SpotiHifi.Tracks.COLUMN_NAME_ALBUM, song.getString("album"));
					        	initialValues.put(SpotiHifi.Tracks.COLUMN_NAME_TRACK_NUMBER, song.getInt("track_number"));
					        	initialValues.put(SpotiHifi.Tracks.COLUMN_NAME_TRACK_ID, song.getString("track_id"));
					        	initialValues.put(SpotiHifi.Tracks.COLUMN_NAME_PLAYLISTS, song.getJSONArray("playlists").toString());

					        	values[i] = initialValues;
							}
							catch(JSONException ex) {
								Log.e(TAG, "invalid song");
							}
						}
						else {
							Log.e(TAG, "song is null");
						}
					}
					// Insert tracks and notify observers.
		            getContentResolver().bulkInsert(SpotiHifi.Tracks.CONTENT_URI, values);
		            getContentResolver().notifyChange(SpotiHifi.Tracks.CONTENT_URI, null);
		            getContentResolver().notifyChange(SpotiHifi.Playlists.CONTENT_URI, null);
		            getContentResolver().notifyChange(SpotiHifi.Artists.CONTENT_URI, null);
				}
				else {
					Log.e(TAG, "sync result has no tracks");
				}

				//mIncarnation = incarnation;
				//mTransaction = transaction;

				//Message msg = mResHandler.obtainMessage(SYNC_COMPLETE_RELOAD_MSG_ID);
				//mResHandler.sendMessage(msg);
			}
		}
		else {
			Log.e(TAG, "sync result == null");
		}
    }

    void responseHandler(JSONObject object)
    {
    	if ( mResultHandler != null ) 
    	{
			Message msg = mResultHandler.obtainMessage(SpotiHifi.RESULT_MSG_ID);
			Bundle bundle = new Bundle();
			bundle.putString("result", object.optString("result"));
			msg.setData(bundle);
			mResultHandler.sendMessage(msg);    		
    	}
    }



    ///////////////////////////////////////////////////////////////////////////
    // Network connection.
    //

    private final class SocketConnection
    {
    	String   mIp;
    	int      mPort;
    	Socket   mSocket;
    	Receiver mReceiver;

    	SocketConnection(String ip, int port)
    	{
    		mIp = ip;
    		mPort = port;
    		mSocket = new Socket();
    		mReceiver = new Receiver();
    	}

    	public synchronized boolean isConnected() {
    		return mSocket.isConnected();
    	}

    	private void connect() throws IOException
    	{
    		mSocket.connect(new InetSocketAddress(mIp, mPort));
    		mReceiver.start();
    	}
    	
    	private void close()
    	{
    		try {
    			mSocket.close();
    		}
    		catch(IOException ex) {
    			Log.e(TAG, "error closing socket");
    		}
    	}
    	
    	public synchronized void sendMessage(String msg) throws IOException
    	{
    		connectHandler();
    		//if ( ! isConnected() ) {
    		//	connect();
    		//}

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

    	private final class Receiver extends Thread
    	{
    		@Override
    		public void run()
    		{
    			while ( true )
    			{
    				try {
		    			if ( isConnected() ) {
		    				String msg = doReceive();
		    				doProcessMessage(msg);
		    			}
		    			else {
		    				Log.i(TAG, "receiver thread not connected");
		    				Thread.sleep(1000);
		    			}
    				}
    				catch(IOException ex) {
    					Log.i(TAG, "receiver receive error");
    					break;
    				}
    				catch(InterruptedException ex) {
    					Log.i(TAG, "receiver thread interrupted");
    				}
    			}
    		}

    		private String doReceive() throws IOException
    		{
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

				return new String(bbuf, "UTF-8");
    		}

    		private void doProcessMessage(String msg)
    		{
    			Log.i(TAG, "receiver received msg, length=" + msg.length());
				try {
					JSONObject object = (JSONObject) new JSONTokener(msg).nextValue();

					// TODO: Check message type.

					int id = object.optInt("id");

					if ( id == 1 ) {
						syncResponseHandler(object);
					}
					else if ( id > 1 ) {
						Log.i(TAG, "result:" + object.toString());
						responseHandler(object);
					}
					else {
						Log.i(TAG, "result:" + object.toString());
					}

				}
				catch (JSONException ex) {
					Log.i(TAG, "receiver prcess message json error");
				}
    		}
    	}
    }

}
