package com.pajtek.spotifind;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.spotify.sdk.android.player.Metadata;
import com.spotify.sdk.android.player.Metadata.Track;
import com.spotify.sdk.android.player.Spotify;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

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

    SpotMarker(int spotKey, String trackName, String trackUri, String artistName, final String albumCoverWebUrl, final LatLng latLng) {
        this.spotKey = spotKey;
        this.trackName = trackName;
        this.trackUri = trackUri;
        this.artistName = artistName;
        this.customMarker =  new MarkerOptions().position(latLng).flat(true);
        new ImageLoader() {
            @Override
            protected void onPostExecute(Bitmap bmp) {
                customMarker = customMarker.icon(BitmapDescriptorFactory.fromBitmap(bmp));
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
                Log.d("SpotMarker",e.toString());
                return null;
            }
        }
    }
}
