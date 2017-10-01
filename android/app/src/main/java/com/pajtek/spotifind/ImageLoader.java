package com.pajtek.spotifind;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.net.URL;

/*
*
*
*
* */

class ImageLoader extends AsyncTask<String, Void, Bitmap> {

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

