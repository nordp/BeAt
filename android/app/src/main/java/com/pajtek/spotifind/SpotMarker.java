package com.pajtek.spotifind;

import com.spotify.sdk.android.player.Metadata;
import com.spotify.sdk.android.player.Metadata.Track;
import com.spotify.sdk.android.player.Spotify;

/**
 * Created by Phnor on 2017-09-30.
 */

public class SpotMarker {
    Track spotTrack;
    int spotKey;

    public SpotMarker(Track spotTrack, int spotKey) {
        this.spotTrack = spotTrack;
        this.spotKey = spotKey;
    }

    @Override
    public String toString() {
        return spotTrack.artistName + " : " + spotTrack.albumName;
    }
}
