package com.pajtek.spotifind;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.net.URL;

/**
 * Created by Phnor on 2017-09-30.
 */

class SpotMarker implements ValueEventListener{

    private String spotKey;

    //Track info
    private TrackInfo trackInfo;

    private static final int ALBUM_ICON_SIZE = 200;

    Marker customMarker;

    SpotMarker(final GoogleMap mMap, String spotKey, LatLng location) {
        this.spotKey = spotKey;
        MarkerOptions markerOptions =  new MarkerOptions().flat(false).anchor(0.5f, 1.0f).position(location).icon(BitmapDescriptorFactory.fromResource(R.drawable.user_position_small));
        customMarker = mMap.addMarker(markerOptions);
    }

    @Override
    public String toString() {
        return trackInfo.artistName + " : " + trackInfo.trackName;
    }

    @Override
    public void onDataChange(DataSnapshot dataSnapshot) {
        if (dataSnapshot.child("name").getValue() == null ||
                dataSnapshot.child("artistName").getValue() == null ||
                dataSnapshot.child("trackId").getValue() == null ||
                dataSnapshot.child("albumCoverWebUrl").getValue() == null
                ){
            return;
        }
        trackInfo = new TrackInfo(
            (String) dataSnapshot.child("name").getValue(),
            (String) dataSnapshot.child("artistName").getValue(),
            "spotify:track:" + dataSnapshot.child("trackId").getValue(),
            (String)dataSnapshot.child("albumCoverWebUrl").getValue());

        new ImageLoader() {
            @Override
            protected void onPostExecute(Bitmap bmp) {
                customMarker.setIcon(BitmapDescriptorFactory.fromBitmap(Bitmap.createScaledBitmap(bmp, ALBUM_ICON_SIZE, ALBUM_ICON_SIZE, false)));
                Log.e("SpotMarker", "Marker added");
            }
        }.execute((String)dataSnapshot.child("albumCoverWebUrl").getValue());
    }

    @Override
    public void onCancelled(DatabaseError databaseError) {

    }

    public TrackInfo getTrackInfo() {
        return trackInfo;
    }

    public LatLng getPosition() {
        return customMarker.getPosition();
    }
}
