package com.pajtek.spotifind;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import static com.spotify.sdk.android.authentication.LoginActivity.REQUEST_CODE;

public class MapsActivity extends FragmentActivity implements
        OnMapReadyCallback, SpotifyPlayer.NotificationCallback, ConnectionStateCallback {

    private final static String SPOTIFY_CLIENT_ID = "c55122e780414b7bbe93c5084097ac8f";
    private final static String SPOTIFY_REDIRECT_URI = "com.pajtek.spotifind://callback";
    private SpotifyPlayer mSpotifyPlayer;

    private final static int REQUEST_LOCATION_CODE = 0x00000001;
    private final static int FETCH_LOCATION_EVERY_X_MS = 5_000;

    private final static float MAP_DEFAULT_ZOOM = 16.0f;

    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationClient;

    private Marker mCurrentPositionMarker;
    Location mLastLocation = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        boolean canFetchNow = setupLocationServices();
        if (canFetchNow) {
            // Make sure we get a position as quickly as possible so the app can "begin"
            fetchCurrentLocation();
        }
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                fetchCurrentLocation();
            }
        }, FETCH_LOCATION_EVERY_X_MS, FETCH_LOCATION_EVERY_X_MS);

        // Authenticate Spotify
        {
            AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(SPOTIFY_CLIENT_ID, AuthenticationResponse.Type.TOKEN, SPOTIFY_REDIRECT_URI);
            builder.setScopes(new String[]{"user-read-private", "streaming"});
            AuthenticationRequest request = builder.build();

            AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
        }
    }

    @Override
    protected void onDestroy() {
        Spotify.destroyPlayer(this);
        super.onDestroy();
    }

    private boolean setupLocationServices() {

        if (mFusedLocationClient == null) {
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        }

        boolean canAccess = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!canAccess) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_CODE);
        }

        // Can I access locations right now, or do I have to wait til permission is granted later?
        return canAccess;
    }

    private void fetchCurrentLocation() {
        // Can't do anything here if we don't have the permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        mFusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    currentLocationUpdated(location);
                    Log.d("MapsActivity", "New location fetched: " + location.toString());
                } else {
                    Log.d("MapsActivity", "New location fetched, but from emulator so it's null.");
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e("MapsActivity", "Can't fetch location!");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION_CODE) {
            if (!Arrays.asList(permissions).contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
                // TODO: Handle the case if the user doesn't give permission!
            }
        }
    }

    private void currentLocationUpdated(Location location) {

        if (mMap == null) {
            Log.d("MapsActivity", "Can't set markers etc. since maps isn't initialized yet!");
            return;
        }

        LatLng position = new LatLng(location.getLatitude(), location.getLongitude());

        updateCurrentLocationMarker(position);



        // First location update
        if (mLastLocation == null) {

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, MAP_DEFAULT_ZOOM);
            mMap.animateCamera(cameraUpdate);

        }

        // TODO
        // TODO Here we can do stuff like query for the next applicable song etc.
        // TODO

        mLastLocation = location;
    }

    private void updateCurrentLocationMarker(LatLng currentPosition) {
        if (mCurrentPositionMarker == null) {
            final MarkerOptions currentPosMarker = new MarkerOptions()
                    .position(currentPosition)
                    .flat(true)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
            mCurrentPositionMarker = mMap.addMarker(currentPosMarker);
        } else {
            mCurrentPositionMarker.setPosition(currentPosition);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        // The next 19 lines of the code are what you need to copy & paste! :)
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                Config playerConfig = new Config(this, response.getAccessToken(), SPOTIFY_CLIENT_ID);
                Spotify.getPlayer(playerConfig, this, new SpotifyPlayer.InitializationObserver() {
                    @Override
                    public void onInitialized(SpotifyPlayer spotifyPlayer) {
                        mSpotifyPlayer = spotifyPlayer;
                        mSpotifyPlayer.addConnectionStateCallback(MapsActivity.this);
                        mSpotifyPlayer.addNotificationCallback(MapsActivity.this);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e("MainActivity", "Could not initialize player: " + throwable.getMessage());
                    }
                });
            }
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        boolean success = mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));
        if (!success) {
            Log.e("MapsActivity", "Could not set the map style!");
        }
    }

    //
    // Spotify lifecycle stuff
    //

    @Override
    public void onPlaybackEvent(PlayerEvent playerEvent) {
        Log.d("MainActivity", "Playback event received: " + playerEvent.name());
        switch (playerEvent) {
            // Handle event type as necessary
            default:
                break;
        }
    }

    @Override
    public void onPlaybackError(Error error) {
        Log.d("MainActivity", "Playback error received: " + error.name());
        switch (error) {
            // Handle error type as necessary
            default:
                break;
        }
    }

    @Override
    public void onLoggedIn() {

        // TODO: This is the line that plays a song.
        mSpotifyPlayer.playUri(null, "spotify:track:2TpxZ7JUBn3uw46aR7qd6V", 0, 0);

    }

    @Override
    public void onLoggedOut() {
        Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Error var1) {
        Log.d("MainActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

}
