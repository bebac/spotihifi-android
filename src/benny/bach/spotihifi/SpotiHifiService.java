package benny.bach.spotihifi;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

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
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

public class SpotiHifiService extends Service implements OnSharedPreferenceChangeListener
{
    private static final String TAG = "SpotiHifiService";

    private static final String TAG_TRACK_ID = "track_id";
    private static final String TAG_PLAYLIST = "playlist";

    private static Timer timer = new Timer();

    private final IBinder mBinder = new SpotiHifiBinder();

    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    private Handler mResultHandler;
    private SocketConnection mConn;

    private class TrackCountDown implements Runnable
    {
        private boolean mStarted;
        //private long    mRemaining;
        private long    mDuration;
        private long    mStartTime;

        public TrackCountDown() {
            //mRemaining = 0;
        }

        public void start(long duration) {
            Log.i(TAG, "track count down start duration=" + duration);
            //mRemaining = duration;
            mDuration = duration*1000;
            mStartTime = System.currentTimeMillis();
            if ( !mStarted ) {
                mStarted = true;
                mServiceHandler.postDelayed(this, 250);
            }
        }

        public void stop() {
            mStarted = false;
        }

        @Override
        public void run() {
            if ( mStarted ) {
                //mRemaining--;
                long elapsed = System.currentTimeMillis() - mStartTime;
                long remaining = mDuration - elapsed;

                //Log.i(TAG, "track count down remaining=" + remaining);

                if ( mResultHandler != null )
                {
                    Message msg = mResultHandler.obtainMessage(SpotiHifi.REMAINING_MSG_ID);
                    Bundle bundle = new Bundle();
                    bundle.putLong("remaining", remaining);
                    msg.setData(bundle);
                    mResultHandler.sendMessage(msg);
                }

                mServiceHandler.postDelayed(this, 250);
            }
        }
    }

    //private Runnable mTrackCountDown = new Runnable() {
    //    @Override
    //    public void run() {
    //        Log.i(TAG, "track count down");
    //        mServiceHandler.postDelayed(this, 1000);
    //    }
    //};
    private TrackCountDown mTrackCountDown = new TrackCountDown();

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
    //  Log.i(TAG, "spotihifi service unbind");
    //    return mAllowRebind;
    //}

    //@Override
    //public void onRebind(Intent intent)
    //{
    //  Log.i(TAG, "spotihifi service unbind");
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

    public void disconnected()
    {
        Message msg = mServiceHandler.obtainMessage(SpotiHifi.SERVICE_DISCONNECTED_MSG_ID);
        mServiceHandler.sendMessage(msg);
    }

    public void index()
    {
        Message msg = mServiceHandler.obtainMessage(SpotiHifi.INDEX_MSG_ID);
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
            case SpotiHifi.SERVICE_DISCONNECTED_MSG_ID:
                disconnectHandler();
                break;
            case SpotiHifi.INDEX_MSG_ID:
                indexRequestHandler();
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

    private void indexRequestHandler()
    {
        try
        {
            JSONObject msg = new JSONObject();
            //JSONObject params = new JSONObject();

            msg.put("jsonrpc", "2.0");
            msg.put("method", "db/index");
            msg.put("params", null);
            msg.put("id", SpotiHifi.INDEX_MSG_ID);

            mConn.sendMessage(msg.toString());
        }
        catch(JSONException ex) {
            Log.e(TAG, "play request json error!");
        }
        catch(IOException ex) {
            Log.e(TAG, "sync request error!");
            disconnectHandler();
        }
    }

    private void playHandler(String playlist)
    {
        if ( mConn == null ) {
            return;
        }

        try {
            JSONObject msg = new JSONObject();

            msg.put("jsonrpc", "2.0");
            msg.put("method", "player/play");

            if ( playlist != null ) {
                JSONObject params = new JSONObject();
                params.put("tag", playlist);
                msg.put("params", params);
            }
            //else {
            //    JSONArray params = new JSONArray();
            //    msg.put("params", params);
            //}

            //msg.put("id", SpotiHifi.PLAY_MSG_ID);

            Log.i(TAG, msg.toString());

            mConn.sendMessage(msg.toString());
        }
        catch(JSONException ex) {
            Log.e(TAG, "play request json error!");
        }
        catch(IOException ex) {
            Log.e(TAG, "player play playlist=" + playlist + " request error!");
            disconnectHandler();
        }
    }

    private void queueRequestHandler(String trackId)
    {
        if ( mConn == null ) {
            return;
        }

        try
        {
            JSONObject msg = new JSONObject();
            JSONObject params = new JSONObject();

            params.put("id", trackId);

            msg.put("jsonrpc", "2.0");
            msg.put("method", "player/queue");
            msg.put("params", params);
            msg.put("id", SpotiHifi.QUEUE_MSG_ID);

            mConn.sendMessage(msg.toString());
        }
        catch(JSONException ex) {
            Log.e(TAG, "queue request json error!");
        }
        catch(IOException ex) {
            Log.e(TAG, "queue request error!");
            disconnectHandler();
        }
    }

    private void playerStopRequestHandler()
    {
        if ( mConn == null ) {
            return;
        }

        try {
            JSONObject msg = new JSONObject();
            JSONArray params = new JSONArray();

            msg.put("jsonrpc", "2.0");
            msg.put("method", "player/stop");
            msg.put("params", params);
            msg.put("id", SpotiHifi.STOP_MSG_ID);

            mConn.sendMessage(msg.toString());
        }
        catch(JSONException ex) {
            Log.e(TAG, "stop request json error!");
        }
        catch(IOException ex) {
            Log.e(TAG, "player stop request error!");
            disconnectHandler();
        }
    }

    private void playerPauseRequestHandler()
    {
        try {
            JSONObject msg = new JSONObject();
            JSONArray params = new JSONArray();

            msg.put("jsonrpc", "2.0");
            msg.put("method", "pause");
            msg.put("params", params);
            msg.put("id", SpotiHifi.PAUSE_MSG_ID);

            mConn.sendMessage(msg.toString());
        }
        catch(JSONException ex) {
            Log.e(TAG, "pause request json error!");
        }
        catch(IOException ex) {
            Log.e(TAG, "player pause request error!");
            disconnectHandler();
        }
    }

    private void playerSkipRequestHandler()
    {
        if ( mConn == null ) {
            return;
        }

        try {
            JSONObject msg = new JSONObject();
            JSONArray params = new JSONArray();

            msg.put("jsonrpc", "2.0");
            msg.put("method", "player/skip");
            msg.put("params", params);
            msg.put("id", SpotiHifi.SKIP_MSG_ID);

            mConn.sendMessage(msg.toString());
        }
        catch(JSONException ex) {
            Log.e(TAG, "skip request json error!");
        }
        catch(IOException ex) {
            Log.e(TAG, "player skip request error!");
            disconnectHandler();
        }
    }

    //private void coverRequestHandler(String trackId, String coverId)
    private void coverRequestHandler(String albumId)
    {
        if ( mConn == null ) {
            return;
        }

        try {
            JSONObject msg = new JSONObject();
            JSONObject params = new JSONObject();

            params.put("album_id", albumId);

            msg.put("jsonrpc", "2.0");
            msg.put("method", "db/cover");
            msg.put("params", params);
            msg.put("id", SpotiHifi.COVER_MSG_ID);

            mConn.sendMessage(msg.toString());
        }
        catch(JSONException ex) {
            Log.e(TAG, "cover request json error!");
        }
        catch(IOException ex) {
            Log.e(TAG, "cover request io error!");
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
            SpotiHifiService.this.disconnected();
        }

    }

    private void disconnectHandler() {
        if ( mConn != null ) {
            Log.i(TAG, "connection lost!");

            getContentResolver().call(Uri.parse("content://benny.bach.spotihifi/service/state"), "disconnected", null, null);

            mTrackCountDown.stop();
            mConn.close();

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            connect();
            //mConn = null;
        }
    }

    private void indexResponseHandler(JSONObject object)
    {
        JSONObject result = object.optJSONObject("result");

        if ( result != null )
        {
            try
            {
                // Create array to pass to bulk insert.
                //ContentValues[] values = new ContentValues[tracks.length()];
                List<ContentValues> values = new ArrayList<ContentValues>();

                //int valueIndex = 0;

                JSONArray artists = result.getJSONArray("artists");
                for (int i = 0; i < artists.length(); i++)
                {
                    JSONObject artist = artists.getJSONObject(i);
                    JSONArray albums = artist.getJSONArray("albums");

                    for (int j = 0; j < albums.length(); j++)
                    {
                        JSONObject album = albums.getJSONObject(j);
                        JSONArray tracks = album.getJSONArray("tracks");

                        for (int k = 0; k < tracks.length(); k++)
                        {
                            JSONObject track = tracks.getJSONObject(k);

                            ContentValues initialValues = new ContentValues();
                            initialValues.put(SpotiHifi.Tracks.COLUMN_NAME_TITLE, track.getString("title"));
                            initialValues.put(SpotiHifi.Tracks.COLUMN_NAME_ARTIST, artist.getString("name"));
                            initialValues.put(SpotiHifi.Tracks.COLUMN_NAME_ALBUM, album.getString("title"));
                            initialValues.put(SpotiHifi.Tracks.COLUMN_NAME_TRACK_NUMBER, track.getInt("tn"));
                            initialValues.put(SpotiHifi.Tracks.COLUMN_NAME_TRACK_ID, track.getString("id"));
                            initialValues.put(SpotiHifi.Tracks.COLUMN_NAME_PLAYLISTS, track.getJSONArray("tags").toString());

                            //values[valueIndex++] = initialValues;
                            values.add(initialValues);
                        }
                    }
                }

                // Insert tracks and notify observers.
                ContentValues[] values_arr = values.toArray(new ContentValues[values.size()]);

                getContentResolver().bulkInsert(SpotiHifi.Tracks.CONTENT_URI, values_arr);
                getContentResolver().notifyChange(SpotiHifi.Tracks.CONTENT_URI, null);
                getContentResolver().notifyChange(SpotiHifi.Playlists.CONTENT_URI, null);
                getContentResolver().notifyChange(SpotiHifi.Artists.CONTENT_URI, null);
            }
            catch(JSONException ex) {
                Log.e(TAG, "index load error!");
            }
        }
        else {
            Log.e(TAG, "sync result == null");
        }
    }

    private void coverResponseHandler(JSONObject object)
    {
        JSONObject result = object.optJSONObject("result");

        if ( result != null )
        {
            try
            {
                //String imageId = result.getString("cover_id");
                String imageData = result.getString("image_data");

                //Log.i(TAG, "loaded cover id : " + imageId);
                Log.i(TAG, "loaded cover id");

                ContentValues values = new ContentValues();

                values.put(SpotiHifi.PlayerState.COLUMN_NAME_COVER_ART, Base64.decode(imageData, Base64.DEFAULT));

                getContentResolver().update(SpotiHifi.PlayerState.CONTENT_URI, values, null, null);
                getContentResolver().notifyChange(SpotiHifi.PlayerState.CONTENT_URI, null);
            }
            catch(JSONException ex) {
                Log.e(TAG, "cover response error:" + result.toString());
            }
        }
    }

    private void playbackEventHandler(JSONObject object)
    {
        JSONObject params = object.optJSONObject("params");

        if ( params != null )
        {
            Log.i(TAG, params.toString());

            String state = params.optString("state");

            ContentValues values = new ContentValues();

            if ( state.equals("playing") )
            {
                JSONObject track = params.optJSONObject("track");

                if ( track != null )
                {
                    values.put(SpotiHifi.PlayerState.COLUMN_NAME_TITLE, track.optString("title", ""));

                    JSONObject artist = track.optJSONObject("artist");
                    if ( artist != null )
                    {
                        values.put(SpotiHifi.PlayerState.COLUMN_NAME_ARTIST, artist.optString("name", ""));
                        values.put(SpotiHifi.PlayerState.COLUMN_NAME_ARTIST_ID, artist.optString("id", ""));
                    }

                    JSONObject album = track.optJSONObject("album");
                    if ( album != null )
                    {
                        values.put(SpotiHifi.PlayerState.COLUMN_NAME_ALBUM, album.optString("title", ""));
                        values.put(SpotiHifi.PlayerState.COLUMN_NAME_ALBUM_ID, album.optString("id", ""));
                    }
                }

                values.put(SpotiHifi.PlayerState.COLUMN_NAME_STATE, "Playing");

                getContentResolver().update(SpotiHifi.PlayerState.CONTENT_URI, values, null, null);
                getContentResolver().notifyChange(SpotiHifi.PlayerState.CONTENT_URI, null);

                if ( track != null )
                {
                    Integer duration_in_secs = track.optInt("duration", 0);

                    mTrackCountDown.start(duration_in_secs);

                    try
                    {
                        JSONObject album = track.optJSONObject("album");
                        if ( album != null )
                        {
                            String album_id = album.getString("id");
                            coverRequestHandler(album_id);
                        }
                    }
                    catch(JSONException ex)
                    {
                        Log.e(TAG, "failed to get cover art ids");
                    }
                }
            }
            else if ( state.equals("paused") )
            {
                values.put(SpotiHifi.PlayerState.COLUMN_NAME_STATE, "Paused");
            }
            else if ( state.equals("stopped") )
            {
                values.put(SpotiHifi.PlayerState.COLUMN_NAME_TITLE, "-");
                values.put(SpotiHifi.PlayerState.COLUMN_NAME_ARTIST, "-");
                values.put(SpotiHifi.PlayerState.COLUMN_NAME_ALBUM, "-");
                values.put(SpotiHifi.PlayerState.COLUMN_NAME_STATE, "Stopped");
                values.put(SpotiHifi.PlayerState.COLUMN_NAME_COVER_ART, "");
                mTrackCountDown.stop();
            }
            else if ( state.equals("skip") )
            {
                values.put(SpotiHifi.PlayerState.COLUMN_NAME_TITLE, "-");
                values.put(SpotiHifi.PlayerState.COLUMN_NAME_ARTIST, "-");
                values.put(SpotiHifi.PlayerState.COLUMN_NAME_ALBUM, "-");
                values.put(SpotiHifi.PlayerState.COLUMN_NAME_STATE, "Skipping");
                values.put(SpotiHifi.PlayerState.COLUMN_NAME_COVER_ART, "");
            }
            else {
                values.put(SpotiHifi.PlayerState.COLUMN_NAME_STATE, "<unknown>");
                mTrackCountDown.stop();
            }

            getContentResolver().update(SpotiHifi.PlayerState.CONTENT_URI, values, null, null);
            getContentResolver().notifyChange(SpotiHifi.PlayerState.CONTENT_URI, null);
        }
        else {
            Log.e(TAG, "playback event params == null");
        }
    }

    synchronized void responseHandler(JSONObject object)
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
            if ( mSocket.isClosed() ) {
                return false;
            }
            else {
                return mSocket.isConnected();
            }
        }

        private void connect() throws IOException
        {
            mSocket.connect(new InetSocketAddress(mIp, mPort));
            mReceiver.start();
        }

        private void close()
        {
            try {
                mReceiver.interrupt();
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
            //  connect();
            //}

            BufferedOutputStream out = new BufferedOutputStream(mSocket.getOutputStream());

            byte[] buf = msg.getBytes("UTF-8");

            //int len = buf.length;

            //out.write((byte)(len>>>24));
            //out.write((byte)(len>>>16));
            //out.write((byte)(len>>>8));
            //out.write((byte)(len));
            //out.write(buf, 0, len);
            out.write(buf, 0, buf.length);
            out.write('\0');
            out.flush();
        }

        private final class Receiver extends Thread
        {
            BufferedReader mBuffer;

            @Override
            public void run()
            {
                try {
                    mBuffer = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
                }
                catch(IOException ex) {
                    Log.i(TAG, "receiver receive error");
                }

                while ( true )
                {
                    try {
                        String msg = doReceive();
                        if ( msg != null ) {
                            doProcessMessage(msg);
                        }
                        else {
                            close();
                            SpotiHifiService.this.disconnected();
                            break;
                        }
                    }
                    catch(IOException ex) {
                        Log.i(TAG, "receiver receive error");
                        //break;
                    }
                }
            }

            private String doReceive() throws IOException
            {
                int c = mBuffer.read();
                // if EOF
                if (c == -1){
                    return null;
                }
                StringBuilder builder = new StringBuilder("");
                // Check if new line or EOF
                while (c != -1 && c != 0){
                    builder.append((char) c);
                    c = mBuffer.read();
                }
                return builder.toString();
            }

            private void doProcessMessage(String msg)
            {
                Log.i(TAG, "receiver received msg, length=" + msg.length());
                try {
                    JSONObject object = (JSONObject) new JSONTokener(msg).nextValue();

                    // TODO: Check message type.

                    int id = object.optInt("id");

                    if ( id == 1 ) {
                        indexResponseHandler(object);
                    }
                    else if ( id == 9 ) {
                        coverResponseHandler(object);
                    }
                    else if ( id > 1 ) {
                        Log.i(TAG, "result:" + object.toString());
                        responseHandler(object);
                    }
                    else
                    {
                        String method = object.optString("method");

                        if ( method.equals("player/event") ) {
                            playbackEventHandler(object);
                        } else {
                            Log.i(TAG, "result:" + object.toString());
                        }
                    }

                }
                catch (JSONException ex) {
                    Log.i(TAG, "receiver prcess message json error");
                }
            }
        }
    }

}
