package com.pajtek.spotifind;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.AudioManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ValueEventListener;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.maps.android.SphericalUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import static com.pajtek.spotifind.R.id.map;
import static com.spotify.sdk.android.authentication.LoginActivity.REQUEST_CODE;

public class MapsActivity extends FragmentActivity implements
        OnMapReadyCallback, SpotifyPlayer.NotificationCallback, ConnectionStateCallback {

    private final static String SPOTIFY_CLIENT_ID = "c55122e780414b7bbe93c5084097ac8f";
    private final static String SPOTIFY_REDIRECT_URI = "com.pajtek.spotifind://callback";
    private SpotifyPlayer mSpotifyPlayer;
    private String mSpotifyAccessToken;
    private boolean mSpotifyLoggedIn = false;

    private List<TrackInfo> mUserTopTracks = new ArrayList<>();

    private final static int REQUEST_LOCATION_CODE = 0x00000001;
    private final static int FETCH_LOCATION_EVERY_X_MS = 5_000;

    private final static float MAP_DEFAULT_ZOOM = 16.0f;
    private final static long FADE_IN_OUT_TIME_MS = 500;

    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationClient;

    GeoFire geoFire = new GeoFire(FirebaseDatabase.getInstance().getReference("/geofire"));
    DatabaseReference spots = FirebaseDatabase.getInstance().getReference("spots");
    private GeoQuery geoQuery;

    private Marker mCurrentPositionMarker;
    LatLng mLastPosition = null;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(map);
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

        // TODO If the user is already "logged in", just remove the log in button & stuff. But maybe not required for this app right now?
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

        if (mMap == null && !mSpotifyLoggedIn) {
            Log.d("MapsActivity", "Can't set markers etc. since maps isn't initialized yet and not yet logged in to Spotify!");
            return;
        }

        LatLng position = new LatLng(location.getLatitude(), location.getLongitude());

        updateCurrentLocationMarker(position);

        // First location update
        if (mLastPosition == null) {
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(position, MAP_DEFAULT_ZOOM);
            mMap.animateCamera(cameraUpdate);
            initGeoFire(position);
        }

        geoQuery.setCenter(new GeoLocation(position.latitude,position.longitude));

        // TODO
        // TODO Here we can do stuff like query for the next applicable song etc.
        // TODO

        mLastPosition = position;
    }

    private void updateCurrentLocationMarker(LatLng currentPosition) {
        if (mCurrentPositionMarker == null) {
            BitmapDescriptor bitmap = BitmapDescriptorFactory.fromResource(R.drawable.user_position_small);
            final MarkerOptions currentPosMarker = new MarkerOptions()
                    .position(currentPosition)
                    .flat(true)
                    .icon(bitmap)
                    .anchor(0.5f, 0.5f);
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
                this.mSpotifyAccessToken = response.getAccessToken();
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

    private void fetchLastPlayedTracks(final String accessToken) {

        class SpotifyRequest extends JsonObjectRequest {
            private SpotifyRequest(int method, String url, JSONObject jsonRequest, Response.Listener<JSONObject> listener, Response.ErrorListener errorListener) {
                super(method, url, jsonRequest, listener, errorListener);
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }
        }

        RequestQueue queue = Volley.newRequestQueue(this);
        final String url = "https://api.spotify.com/v1/me/top/tracks?time_range=short_term&limit=10&offset=0";

        JsonObjectRequest topTracksRequest = new SpotifyRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

                @Override
                public void onResponse(JSONObject response) {
                    try {

                        JSONArray items = response.getJSONArray("items");
                        for (int i = 0; i < items.length(); i++) {

                            JSONObject trackObject = items.getJSONObject(i);
                            JSONObject firstArtist = trackObject.getJSONArray("artists").getJSONObject(0);

                            String artistName = firstArtist.getString("name");
                            String trackName = trackObject.getString("name");
                            String trackUri = trackObject.getString("uri");

                            TrackInfo track = new TrackInfo(artistName, trackName, trackUri);
                            mUserTopTracks.add(track);

                        }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                topTracksLoaded();
                            }
                        });

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e("MapsActivity", "Can't parse or download the top tracks response JSON");
                }
            }
        );

        queue.add(topTracksRequest);
    }

    private void topTracksLoaded() {
        Log.d("MapsActivity", mUserTopTracks.toString());

        TrackInfo track = mUserTopTracks.get(new Random().nextInt(mUserTopTracks.size()));
        addSongToCurrentLocation(track);
    }

    private void addSongToCurrentLocation(TrackInfo track) {
        if (mLastPosition == null) {
            Log.e("MapsActivity", "No location available! Presumably since it's running in the emulator?!");
            return;
        }

        LatLng pos = new LatLng(mLastPosition.latitude, mLastPosition.longitude);
        String trackUri = track.trackUri;
        String trackId = trackUri.split(":")[2];

        String unixTimeId = String.valueOf(new Date().getTime());

        DatabaseReference base = spots.child(unixTimeId);
        base.child("trackId").setValue(trackId);

        Map<String, Object> locMap = new HashMap<>();
        locMap.put("lat", pos.latitude);
        locMap.put("lon", pos.longitude);
        base.child("loc").updateChildren(locMap);
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

        mMap.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {
            @Override
            public void onCameraMove() {
                if (geoQuery != null && mSpotifyLoggedIn) {
                    geoQuery.setRadius(getVisibleRegion());
                }
            }
        });

        boolean success = mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));
        if (!success) {
            Log.e("MapsActivity", "Could not set the map style!");
        }
    }

    //
    // Spotify lifecycle stuff
    //

    private void fadeInSong(final String trackId) {

        final float targetMaxVolume = getSpotifyPlayerVolume();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (mSpotifyPlayer.getPlaybackState().isPlaying) {
                    // If currently playing, fade out, then change song and fade in

                    ValueAnimator vaOut = ValueAnimator.ofFloat(targetMaxVolume, 0.0f);
                    vaOut.setDuration(FADE_IN_OUT_TIME_MS);
                    vaOut.setRepeatCount(0);
                    vaOut.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        public void onAnimationUpdate(ValueAnimator animation) {
                            float animatedVolume = (float)animation.getAnimatedValue();
                            setSpotifyPlayerVolume(animatedVolume);
                        }
                    });
                    vaOut.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {

                            mSpotifyPlayer.playUri(null, trackId, 0, 0);

                            ValueAnimator vaIn = ValueAnimator.ofFloat(0.0f, targetMaxVolume);
                            vaIn.setDuration(FADE_IN_OUT_TIME_MS);
                            vaIn.setRepeatCount(0);
                            vaIn.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    float animatedVolume = (float)animation.getAnimatedValue();
                                    setSpotifyPlayerVolume(animatedVolume);
                                }
                            });
                            vaIn.start();

                        }
                    });
                    vaOut.start();
                } else {
                    // If NOT currently playing, change song and fade in

                    setSpotifyPlayerVolume(0.0f);
                    mSpotifyPlayer.playUri(new Player.OperationCallback() {
                        @Override
                        public void onSuccess() {

                            Log.e("MapsActivity", "Playing new song!");
                            ValueAnimator vaIn = ValueAnimator.ofFloat(0.0f, targetMaxVolume);
                            vaIn.setDuration(FADE_IN_OUT_TIME_MS);
                            vaIn.setRepeatCount(0);
                            vaIn.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    float animatedVolume = (float)animation.getAnimatedValue();
                                    setSpotifyPlayerVolume(animatedVolume);
                                }
                            });
                            vaIn.start();

                        }

                        @Override
                        public void onError(Error error) {
                            Log.e("MapsActivity", "Can't play Spotify URI");
                        }

                    }, trackId, 0, 0);

                }

            }
        });
    }

    private void setSpotifyPlayerVolume(float volume) {
        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        final int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        int volumeToSet = (int)((float)maxVolume * volume);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToSet, 0);
    }

    private float getSpotifyPlayerVolume() {
        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        final int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int volume = am.getStreamVolume(AudioManager.STREAM_MUSIC);

        return (float)volume / (float)maxVolume;
    }

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
        Log.d("MapsActivity", "User logged in");

        mSpotifyLoggedIn = true;

        Log.d("MapsActivity", "Access token: " + this.mSpotifyAccessToken);
        fetchLastPlayedTracks(this.mSpotifyAccessToken);

        // Fade out green tint when logged in
        ImageView imageView = (ImageView) findViewById(R.id.gradientImageView);
        ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(imageView, "alpha", 0.2f);
        alphaAnimator.setDuration(2_000);
        alphaAnimator.start();

        /*
        fadeInSong("spotify:track:2TpxZ7JUBn3uw46aR7qd6V");
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                fadeInSong("spotify:track:5WSdMcWTKRdN1QYVJHJWxz");
            }
        }, 10_000);
        */
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

    public void loginButtonPressed(View view){
        Log.d("LOGGED", "loginButtonPressed: WorkS!!");

        // Authenticate Spotify
        {
            AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(SPOTIFY_CLIENT_ID, AuthenticationResponse.Type.TOKEN, SPOTIFY_REDIRECT_URI);
            builder.setScopes(new String[]{"streaming", "user-read-birthdate", "user-read-email", "user-read-private", "user-top-read"});
            AuthenticationRequest request = builder.build();

            AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
        }

        view.setVisibility(View.INVISIBLE);
        // Fade out green tint when logged in! (in callback later)
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

    //
    // Init Geofire
    //
    private void initGeoFire(LatLng latLng) {
        geoQuery = geoFire.queryAtLocation(new GeoLocation(latLng.latitude, latLng.longitude), getVisibleRegion());
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                addSpotMarker(key, location);
            }

            @Override
            public void onKeyExited(String key) {
                removeSpotMarker(key);
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                //Inte särskilt troligt
            }

            @Override
            public void onGeoQueryReady() {
                //???
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
                Log.d("MapsActivity", error.toString());
            }
        });
    }

    private void removeSpotMarker(String key) {
        //TODO Remove marker from mMap
    }

    private void addSpotMarker(final String key, final GeoLocation location) {
        spots.child(key).child("info").addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                SpotMarker spotMarker = new SpotMarker(mMap,
                        key,
                        dataSnapshot.child("name").getValue().toString(),
                        dataSnapshot.child("trackId").getValue().toString(),
                        dataSnapshot.child("artistName").getValue().toString(),
                        dataSnapshot.child("albumCoverWebUrl").getValue().toString(),
                        new LatLng(location.latitude,location.longitude));
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d("MapsActivity", databaseError.toString());
            }
        });


        //TODO refresh picture of marker
    }

    //UTIL
    private double getVisibleRegion() {
        //Calculate the current visible region
        VisibleRegion visibleRegion = mMap.getProjection().getVisibleRegion();
        return SphericalUtil.computeDistanceBetween(visibleRegion.farLeft, mMap.getCameraPosition().target);
    }
}
