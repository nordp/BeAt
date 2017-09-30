package com.pajtek.spotifind;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.net.URL;

/**
 * Created by Phnor on 2017-09-30.
 */

class SpotMarker {

    private int spotKey;

    //Track info
    private String trackName;
    private String trackUri;
    private String artistName;

    MarkerOptions customMarker;

    SpotMarker(final GoogleMap mMap, int spotKey, String trackName, String trackId, String artistName, final String albumCoverWebUrl, final LatLng latLng) {
        this.spotKey = spotKey;
        this.trackName = trackName;
        this.trackUri = "spotify:track:" + trackId;
        this.artistName = artistName;
        this.customMarker =  new MarkerOptions().position(latLng).flat(false);
        new ImageLoader() {
            @Override
            protected void onPostExecute(Bitmap bmp) {
                customMarker = customMarker.icon(BitmapDescriptorFactory.fromBitmap(bmp));
                mMap.addMarker(customMarker);
                Log.e("SpotMarker", "Marker added");
            }
        }.execute(albumCoverWebUrl);
    }

    @Override
    public String toString() {
        return artistName + " : " + trackName;
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
