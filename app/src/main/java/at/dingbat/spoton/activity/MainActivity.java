package at.dingbat.spoton.activity;

import android.animation.ValueAnimator;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import java.util.Calendar;

import at.dingbat.spoton.R;
import at.dingbat.spoton.fragment.CreditsFragment;
import at.dingbat.spoton.fragment.PlayerFragment;
import at.dingbat.spoton.models.ParcelableArtist;
import at.dingbat.spoton.fragment.ArtistFragment;
import at.dingbat.spoton.fragment.SearchFragment;
import at.dingbat.spoton.models.ParcelableTrack;
import at.dingbat.spoton.service.PlayerService;
import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;


public class MainActivity extends ActionBarActivity {

    public static final String PARCEL_RECYCLER_ADAPTER = "recycler_adapter";
    public static final String PARCEL_TOOLBAR_VISIBLE = "toolbar_visible";
    private static final int REQUEST_CODE = 666;
    private static final String REDIRECT_URI = "spoton://callback";

    private SpotifyApi api;
    private SpotifyService spotify;
    private String token = "";

    private Toolbar toolbar;
    private RelativeLayout root;

    private ValueAnimator hideToolbar;
    private ValueAnimator showToolbar;

    private boolean showingToolbar = false;
    private boolean hidingToolbar = false;
    private boolean toolbarVisible = false;

    private int colorPrimary;

    private Toast toast;

    private SearchFragment searchFragment;
    private ArtistFragment artistFragment;
    private CreditsFragment creditsFragment;
    private DialogFragment playerFragment;

    private PlayerService service;
    private boolean bound = false;

    private SharedPreferences prefs;

    private boolean isArtistShown = false;
    private boolean isPlayerShown = false;
    private boolean isCreditsShown = false;

    private Runnable onServiceConnected;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        root = (RelativeLayout) findViewById(R.id.activity_main_root);

        toolbar = (Toolbar) findViewById(R.id.activity_main_toolbar);
        setSupportActionBar(toolbar);

        prefs = getSharedPreferences("spoton", MODE_PRIVATE);

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });

        token = getAccessToken();
        long expiresIn = getAccessTokenExpirationTime();


        if (!token.equals("") && expiresIn > Calendar.getInstance().getTimeInMillis()) {

            api = new SpotifyApi();
            api.setAccessToken(token);
            spotify = api.getService();

        } else {

            AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder("6d13aa27037a4863a1c505382b0ce9a7", AuthenticationResponse.Type.TOKEN, REDIRECT_URI);

            builder.setScopes(new String[]{"streaming"});
            AuthenticationRequest request = builder.build();

            AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);

        }


        if (savedInstanceState != null) {
            Intent intent = new Intent(this, PlayerService.class);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
            isPlayerShown = savedInstanceState.getBoolean("isPlayerShown");
            //if(isPlayerShown) onBackPressed();
            boolean tv = savedInstanceState.getBoolean(PARCEL_TOOLBAR_VISIBLE);
            if (tv) showToolbar();
            else hideToolbar();
        } else {
            showSearch();
        }

        colorPrimary = getResources().getColor(R.color.colorPrimary);

    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, PlayerService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(bound) {
            unbindService(connection);
            bound = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    public PlayerService getService() {
        return service;
    }

    public void showSearch() {
        searchFragment = new SearchFragment();
        getFragmentManager().beginTransaction().add(R.id.activity_main_search_fragment, searchFragment, "Search").commit();
    }

    public void showArtist(ParcelableArtist artist) {
        isArtistShown = true;
        Bundle bundle = new Bundle();
        bundle.putParcelable(ArtistFragment.TAG_ARTIST, artist);
        artistFragment = new ArtistFragment();
        artistFragment.setArguments(bundle);
        getFragmentManager().beginTransaction().add(R.id.activity_main_artist_fragment, artistFragment, "Artist").addToBackStack("Artist").commit();
    }

    public void showPlayer() {
        isPlayerShown = true;
        playerFragment = new PlayerFragment();
        TelephonyManager telephony = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephony.getPhoneType() == TelephonyManager.PHONE_TYPE_NONE) {
            playerFragment.setShowsDialog(true);
            playerFragment.show(getFragmentManager(), "Player");
        } else {
            getFragmentManager().beginTransaction().add(R.id.activity_main_player_fragment, playerFragment, "Player").addToBackStack("Player").commit();
        }
    }

    public void showCredits() {
        isCreditsShown = true;
        creditsFragment = new CreditsFragment();
        getFragmentManager().beginTransaction().add(R.id.activity_main_credits_fragment, creditsFragment, "Credits").addToBackStack("Credits").commit();
    }

    public void search(String term) {
        if(searchFragment != null) searchFragment.search(term);
    }

    public SpotifyService getSpotify() {
        return spotify;
    }

    public void setToolbarText(String text) {
        toolbar.setTitle(text);
    }

    public void showToolbarBackArrow(boolean show) {
        getSupportActionBar().setDisplayHomeAsUpEnabled(show);
    }

    public void showToolbar() {
        if(Color.alpha(((ColorDrawable)toolbar.getBackground()).getColor()) == 0 && !toolbarVisible) {
            toolbarVisible = true;
            showingToolbar = true;
            int start_value = 0;
            int time = 200;
            if (hidingToolbar) {
                hidingToolbar = false;
                start_value = (int) hideToolbar.getAnimatedValue();
                time = (int) hideToolbar.getCurrentPlayTime();
                hideToolbar.cancel();
            }
            showToolbar = ValueAnimator.ofInt(start_value, 255);
            showToolbar.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    int value = (Integer) valueAnimator.getAnimatedValue();

                    int color = Color.argb(value, Color.red(colorPrimary), Color.green(colorPrimary), Color.blue(colorPrimary));
                    toolbar.setBackgroundColor(color);

                    if(value == 255) showingToolbar = false;
                }
            });
            showToolbar.setDuration(time);
            showToolbar.start();
        }
    }

    public void hideToolbar() {
        if(Color.alpha(((ColorDrawable)toolbar.getBackground()).getColor()) == 255 && toolbarVisible) {
            toolbarVisible = false;
            hidingToolbar = true;
            int start_value = 255;
            int time = 200;
            if (showingToolbar) {
                showingToolbar = false;
                start_value = (int) showToolbar.getAnimatedValue();
                time = (int) showToolbar.getCurrentPlayTime();
                showToolbar.cancel();
            }
            hideToolbar = ValueAnimator.ofInt(start_value, 0);
            hideToolbar.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    int value = (Integer) valueAnimator.getAnimatedValue();

                    int color = Color.argb(value, Color.red(colorPrimary), Color.green(colorPrimary), Color.blue(colorPrimary));
                    toolbar.setBackgroundColor(color);

                    if(value == 0) hidingToolbar = false;
                }
            });
            hideToolbar.setDuration(time);
            hideToolbar.start();
        }
    }

    public void showToast(String message, int duration) {
        if(toast != null) toast.cancel();
        toast = Toast.makeText(this, message, duration);
        toast.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_search, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_credits) {
            showCredits();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if(getFragmentManager().getBackStackEntryCount() > 0) {
            if(getFragmentManager().getBackStackEntryCount() == 1) {
                showToolbarBackArrow(false);
                setToolbarText(getResources().getString(R.string.app_name));
            }
            getFragmentManager().popBackStack();
        } else super.onBackPressed();
        if(isPlayerShown) {
            service.pause();
            isPlayerShown = false;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(PARCEL_TOOLBAR_VISIBLE, toolbarVisible);
        outState.putBoolean("isPlayerShown", isPlayerShown);
    }

    private void saveAccessToken(String token, long expiresIn) {
        prefs.edit().putString("token", token).putLong("expiresIn", expiresIn).commit();
    }

    private String getAccessToken() {
        return prefs.getString("token", "");
    }

    private long getAccessTokenExpirationTime() {
        return prefs.getLong("expiresIn", Long.MAX_VALUE);
    }

    public void setOnServiceConnected(Runnable onServiceConnected) {
        this.onServiceConnected = onServiceConnected;
        if(bound == true) onServiceConnected.run();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);

            switch (response.getType()) {
                case TOKEN:
                    token = response.getAccessToken();
                    long millis = Calendar.getInstance().getTimeInMillis() + response.getExpiresIn()*1000;
                    saveAccessToken(response.getAccessToken(), millis);

                    api = new SpotifyApi();
                    api.setAccessToken(token);
                    spotify = api.getService();

                    showSearch();

                    break;
                case ERROR:
                    Log.e("", "Something went terribly wrong: "+response.getError());
                    break;
            }
        }
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d("", "Service connected!");
            PlayerService.Binder binder = (PlayerService.Binder) iBinder;
            service = binder.getService();
            bound = true;
            if(onServiceConnected != null) onServiceConnected.run();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d("", "Service disconnected!");
            unbindService(connection);
            bound = false;
        }
    };

}
