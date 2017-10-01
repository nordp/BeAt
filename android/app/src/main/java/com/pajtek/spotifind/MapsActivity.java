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
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

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
import com.google.maps.android.MarkerManager;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import pl.bclogic.pulsator4droid.library.PulsatorLayout;

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

    Map<String, Marker> placedSpotMarkers = new HashMap<>();
    private GeoQuery placedSpotQuery;
    private PulseMarkerHandler pulseMarkerHandler;

    Map<String, LatLng> animatedMarkers = new HashMap<>();
    private GeoQuery animatedMarkerQuery;
    private final static double ANIMATE_MARKER_WITHIN_RADIUS_M = 10000;


    Map<String, SpotMarker> visibleAlbumSpotMarkers = new HashMap<>();
    private GeoQuery visibleAlbumSpotQuery;
    private final static double PLAY_SONG_WITHIN_RADIUS_M = 100;

    private Marker mCurrentPositionMarker;
    LatLng mLastPosition = null;
    private PulsatorLayout pulsatorLayout;
    private BottomSheetDialogFragment tracklist;

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

        // Set tracks sheet invisible until we have songs
        this.findViewById(R.id.tracks_sheet).setVisibility(View.INVISIBLE);
        this.findViewById(R.id.addOverLay).setVisibility(View.INVISIBLE);

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
                LatLng latLng;
                if (location == null) {
                    Log.d("MapsActivity", "New location fetched, but from emulator so it's null. Faking Spotify location");
                    latLng = new LatLng(57.704217, 11.965250);
                } else {
                    Log.d("MapsActivity", "New location fetched: " + location.toString());
                    latLng = new LatLng(location.getLatitude(), location.getLongitude());
                }
                currentLocationUpdated(latLng);
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

    private void currentLocationUpdated(LatLng position) {

        if (mMap == null || !mSpotifyLoggedIn) {
            Log.d("MapsActivity", "Can't set markers etc. since maps isn't initialized yet and not yet logged in to Spotify!");
            return;
        }

        updateCurrentLocationMarker(position);

        // First location update
        if (mLastPosition == null) {
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(position, MAP_DEFAULT_ZOOM);
            mMap.animateCamera(cameraUpdate);
            initGeoFire(position);
        }

        animatedMarkerQuery.setCenter(new GeoLocation(position.latitude, position.longitude));
        visibleAlbumSpotQuery.setCenter(new GeoLocation(position.latitude, position.longitude));

        // Update closest song/marker
        SpotMarker closestMarker = getMarkerClosestToPosition(position);
        if (closestMarker != null) {
            double distance = distanceBetweenLocations(closestMarker.getPosition(), position);
            String currentlyPlayingURI = mSpotifyPlayer.getMetadata().currentTrack != null
                    ? mSpotifyPlayer.getMetadata().currentTrack.uri
                    : null;

            // Play song if it's within the radius and isn't already playing
            if (distance <= PLAY_SONG_WITHIN_RADIUS_M && !closestMarker.getTrackInfo().trackUri.equals(currentlyPlayingURI)) {
                TrackInfo track = closestMarker.getTrackInfo();
                fadeInSong(track);
            }
        }

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

    private SpotMarker getMarkerClosestToPosition(LatLng position) {
        SpotMarker closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;

        for (SpotMarker marker : visibleAlbumSpotMarkers.values()) {
            double distance = distanceBetweenLocationsRelative(position, marker.getPosition());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = marker;
            }
        }

        return closest;
    }

    private double distanceBetweenLocationsRelative(LatLng p1, LatLng p2) {
        double x = p1.longitude - p2.longitude;
        double y = p1.latitude - p2.latitude;
        return Math.sqrt(x * x + y * y);
    }

    private double distanceBetweenLocations(LatLng p1, LatLng p2) {
        double R = 6378.137; // Radius of earth in KM
        double dLat = p2.latitude * Math.PI / 180 - p1.latitude * Math.PI / 180;
        double dLon = p2.longitude * Math.PI / 180 - p1.longitude * Math.PI / 180;
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(p1.latitude * Math.PI / 180) * Math.cos(p2.latitude * Math.PI / 180) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double d = R * c;
        return d * 1000; // meters
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
        Log.d("MapsActivity", "Top tracks loaded (num=" + mUserTopTracks.size() + "): " + mUserTopTracks.toString());

        final int[] trackIds = new int[]{ R.id.track1, R.id.track2, R.id.track3, R.id.track4, R.id.track5 };
        for (int i = 0; i < trackIds.length; i++) {

            TrackInfo trackInfo = mUserTopTracks.get(i);

            TextView textView = (TextView) this.findViewById(trackIds[i]);
            textView.setText(trackInfo.artistName + " – " + trackInfo.trackName);

        }

        View tracksSheet = this.findViewById(R.id.tracks_sheet);
        tracksSheet.setAlpha(0.0f);
        tracksSheet.setVisibility(View.VISIBLE);
        ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(tracksSheet, "alpha", 1.0f);
        alphaAnimator.setDuration(800);
        alphaAnimator.start();
    }

    private TrackInfo trackSelectedToPush = null;

    public void topTrackPressed(View view) {
        final int[] trackIds = new int[]{ R.id.track1, R.id.track2, R.id.track3, R.id.track4, R.id.track5 };

        for (int i = 0; i < trackIds.length; i++) {
            if (view.getId() == trackIds[i]) {

                TrackInfo selectedTrack = mUserTopTracks.get(i);
                trackSelectedToPush = selectedTrack;

                View addOverlay = findViewById(R.id.addOverLay);
                ((TextView)findViewById(R.id.artistName)).setText(selectedTrack.artistName);
                ((TextView)findViewById(R.id.trackName)).setText(selectedTrack.trackName);
                addOverlay.setVisibility(View.VISIBLE);

                return;

            }
        }

        // Something went wrong, couldn't find the song
        Log.e("MapsActivity", "The user pressed some top track that doesn't exist?!");
    }

    public void cancelAddSongPressed(View view) {
        trackSelectedToPush = null;
        View addOverlay = findViewById(R.id.addOverLay);
        addOverlay.setVisibility(View.INVISIBLE);
        View trackList = findViewById(R.id.tracks_sheet);
        addButtonPressed(trackList);
    }

    public void confirmAddSongPressed(View view) {
        if (trackSelectedToPush != null) {
            addSongToCurrentLocation(trackSelectedToPush);
        }
        View addOverlay = findViewById(R.id.addOverLay);
        addOverlay.setVisibility(View.INVISIBLE);
        View trackList = findViewById(R.id.tracks_sheet);
        addButtonPressed(trackList);
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

        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.getUiSettings().setIndoorLevelPickerEnabled(false);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.getUiSettings().setScrollGesturesEnabled(false);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(49.008587, 20.961517), 0.0f));

        mMap.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {
            @Override
            public void onCameraMove() {
                if (placedSpotQuery != null && mSpotifyLoggedIn) {
                    GeoLocation loc = new GeoLocation(mMap.getCameraPosition().target.latitude,mMap.getCameraPosition().target.longitude);
                    placedSpotQuery.setLocation(loc, getVisibleRegion());
                }
                pulseMarkerHandler.update();
            }
        });

        boolean success = mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));
        if (!success) {
            Log.e("MapsActivity", "Could not set the map style!");
        }

        initPulseMarkerHandler();
    }

    //
    // Spotify lifecycle stuff
    //

    private void fadeInSong(final String trackId) {
        fadeInSong(new TrackInfo("NOT SET", "NOT SET", trackId));
    }

    private void fadeInSong(final TrackInfo track) {

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
                            float animatedVolume = (float) animation.getAnimatedValue();
                            setSpotifyPlayerVolume(animatedVolume);
                        }
                    });
                    vaOut.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {

                            mSpotifyPlayer.playUri(null, track.trackUri, 0, 0);

                            ValueAnimator vaIn = ValueAnimator.ofFloat(0.0f, targetMaxVolume);
                            vaIn.setDuration(FADE_IN_OUT_TIME_MS);
                            vaIn.setRepeatCount(0);
                            vaIn.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    float animatedVolume = (float) animation.getAnimatedValue();
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
                                    float animatedVolume = (float) animation.getAnimatedValue();
                                    setSpotifyPlayerVolume(animatedVolume);
                                }
                            });
                            vaIn.start();

                        }

                        @Override
                        public void onError(Error error) {
                            Log.e("MapsActivity", "Can't play Spotify URI");
                        }

                    }, track.trackUri, 0, 0);

                }

            }
        });
    }

    private void setSpotifyPlayerVolume(float volume) {
        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        final int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        int volumeToSet = (int) ((float) maxVolume * volume);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToSet, 0);
    }

    private float getSpotifyPlayerVolume() {
        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        final int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int volume = am.getStreamVolume(AudioManager.STREAM_MUSIC);

        return (float) volume / (float) maxVolume;
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

        // Enable scroll gestures
        mMap.getUiSettings().setScrollGesturesEnabled(true);
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

    public void loginButtonPressed(View view) {
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
    public void debugButtonPressed(View view){
        Log.d("LOGGED", mMap.getCameraPosition().target.toString());
        topTracksLoaded();
    }

    public void addButtonPressed(View view) {
        View trackList = findViewById(R.id.tracks_sheet);
        BottomSheetBehavior trackListBehavior = BottomSheetBehavior.from(trackList);
        if(trackListBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
            trackListBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            view.findViewById(R.id.tracklistButton).setRotation(45);
        }
        else {
            trackListBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            view.findViewById(R.id.tracklistButton).setRotation(0);
        }
    }

    private void replaceAnimation() {

    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

    //
    // Init Geofire
    //
    private void initGeoFire(LatLng latLng) {
        Log.i("MapsActivity", "GEOFIRE INITIATED");
        GeoLocation loc = new GeoLocation(latLng.latitude, latLng.longitude);
        placedSpotQuery = geoFire.queryAtLocation(loc, getVisibleRegion());
        placedSpotQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                LatLng markerLoc = new LatLng(location.latitude, location.longitude);

                MarkerOptions placedMarkerOptions = new MarkerOptions().flat(true).anchor(0.5f, 0.5f).position(markerLoc).icon(BitmapDescriptorFactory.fromResource(R.drawable.song_point_small));
                Marker placedMarker = mMap.addMarker(placedMarkerOptions);

                placedSpotMarkers.put(key, placedMarker);
                Log.i("MapsActivity", "MARKER PUT! key: " + key);
            }

            @Override
            public void onKeyExited(String key) {
                Log.i("MapsActivity", "Removing " + key);
                placedSpotMarkers.remove(key).remove();
                Log.i("MapsActivity", "MARKER REMOVED!");
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                //Inte särskilt troligt
            }

            @Override
            public void onGeoQueryReady() {
                Log.d("MapsActivity", "GEOQUERY READY");
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
                Log.e("MapsActivity", error.toString());
            }
        });

        animatedMarkerQuery = geoFire.queryAtLocation(loc, ANIMATE_MARKER_WITHIN_RADIUS_M / 1000);
        animatedMarkerQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                animatedMarkers.put(key, new LatLng(location.latitude, location.longitude));

                //TODO CALL ANIMATION UPDATE ONCE
            }

            @Override
            public void onKeyExited(String key) {
                animatedMarkers.remove(key);
                //TODO CALL ANIMATION UPDATE ONCE
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                //ORIMLIGT
            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
                Log.e("MapsActivity", error.toString());
            }
        });

        visibleAlbumSpotQuery = geoFire.queryAtLocation(loc, PLAY_SONG_WITHIN_RADIUS_M / 1000);
        visibleAlbumSpotQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                SpotMarker newMarker = new SpotMarker(mMap, key, new LatLng(location.latitude, location.longitude));
                visibleAlbumSpotMarkers.put(key, newMarker);
                spots.child(key).child("info").addValueEventListener(newMarker);
            }

            @Override
            public void onKeyExited(String key) {
                SpotMarker oldMarker = visibleAlbumSpotMarkers.get(key);
                if (oldMarker == null)
                    return;
                spots.child(key).child("info").removeEventListener(oldMarker);
                oldMarker.customMarker.remove();
                visibleAlbumSpotMarkers.remove(key);
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });


    }

    private void initPulseMarkerHandler(){

        Log.d("Tagg", "init");
        List<PulsatorLayout> pulsatorLayoutArrayList = new ArrayList<>();
        pulsatorLayoutArrayList.add((PulsatorLayout) findViewById(R.id.pulsator1));
        pulsatorLayoutArrayList.add((PulsatorLayout) findViewById(R.id.pulsator2));
        pulsatorLayoutArrayList.add((PulsatorLayout) findViewById(R.id.pulsator3));
        pulsatorLayoutArrayList.add((PulsatorLayout) findViewById(R.id.pulsator4));
        pulsatorLayoutArrayList.add((PulsatorLayout) findViewById(R.id.pulsator5));
        pulsatorLayoutArrayList.add((PulsatorLayout) findViewById(R.id.pulsator6));
        pulsatorLayoutArrayList.add((PulsatorLayout) findViewById(R.id.pulsator7));
        pulsatorLayoutArrayList.add((PulsatorLayout) findViewById(R.id.pulsator8));
        pulsatorLayoutArrayList.add((PulsatorLayout) findViewById(R.id.pulsator9));
        pulsatorLayoutArrayList.add((PulsatorLayout) findViewById(R.id.pulsator10));
        pulsatorLayoutArrayList.add((PulsatorLayout) findViewById(R.id.pulsator11));
        pulsatorLayoutArrayList.add((PulsatorLayout) findViewById(R.id.pulsator12));

        pulseMarkerHandler = new PulseMarkerHandler(animatedMarkers, mMap, pulsatorLayoutArrayList);
    }

    //UTIL
    private double getVisibleRegion() {
        //Calculate the current visible region
        VisibleRegion visibleRegion = mMap.getProjection().getVisibleRegion();
        return SphericalUtil.computeDistanceBetween(visibleRegion.farLeft, mMap.getCameraPosition().target);
    }
}
