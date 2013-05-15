package benny.bach.spotihifi;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;

public class SettingsActivity extends Activity implements OnSharedPreferenceChangeListener {

	public static final String SERVER_IP = "pref_server_ip";
	public static final String SERVER_PORT = "pref_server_port";
	
	private SettingsFragment mSettingsFragment;	
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSettingsFragment = new SettingsFragment();
        
        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mSettingsFragment)
                .commit();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        Log.i("Settings", "resume");

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        
        setEditTextSummary("pref_server_ip");
        setEditTextSummary("pref_server_port");
    }
    
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    	Log.i("Settings", "pref changed " + key);
    	// NOTE: I am sure there is a sweeter way to do this.
    	if ( key.equals(SERVER_IP) ) {
    		setEditTextSummary(key);
    	}
    	else if ( key.equals(SERVER_PORT) ) {
    		setEditTextSummary(key);
    	} 
    }
    
    private void setEditTextSummary(String key) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        String value = sp.getString(key, "");
        
        Preference prefServerIp = mSettingsFragment.findPreference(key);
        prefServerIp.setSummary(value);
    }
    
}
