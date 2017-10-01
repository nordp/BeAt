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
    private String trackName;
    private String trackUri;
    private String artistName;

    Marker customMarker;

    SpotMarker(final GoogleMap mMap, String spotKey, LatLng location) {
        this.spotKey = spotKey;
        MarkerOptions markerOptions =  new MarkerOptions().flat(false).anchor(0.5f, 1.0f).position(location).icon(BitmapDescriptorFactory.fromResource(R.drawable.user_position_small));
        customMarker = mMap.addMarker(markerOptions);
    }

    @Override
    public String toString() {
        return artistName + " : " + trackName;
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

        trackName = (String) dataSnapshot.child("name").getValue();
        artistName = (String) dataSnapshot.child("artistName").getValue();
        trackUri = "spotify:track:" + dataSnapshot.child("trackId").getValue();

        new ImageLoader() {
            @Override
            protected void onPostExecute(Bitmap bmp) {
                customMarker.setIcon(BitmapDescriptorFactory.fromBitmap(bmp));
                Log.e("SpotMarker", "Marker added");
            }
        }.execute((String)dataSnapshot.child("albumCoverWebUrl").getValue());

    }

    @Override
    public void onCancelled(DatabaseError databaseError) {

    }

    public TrackInfo getTrackInfo() {
        return new TrackInfo(artistName, trackName, trackUri);
    }

    public LatLng getPosition() {
        return customMarker.getPosition();
    }

    private class ImageLoader extends AsyncTask<String, Void, Bitmap>{

        @Override
        protected Bitmap doInBackground(String... params) {
            try {
                URL url = new URL(params[0]);
                return BitmapFactory.decodeStream(url.openConnection().getInputStream());
            } catch(IOException e) {
                Log.e("SpotMarker","ERROR LOADING BITMAP" + e.toString());
                return null;
            }
        }
    }
}
