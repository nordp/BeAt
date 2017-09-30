package com.pajtek.spotifind;

/**
 * Created by Simon on 2017-09-30.
 */

public class TrackInfo {

    public TrackInfo(String artistName, String trackName, String trackUri) {
        this.artistName = artistName;
        this.trackName = trackName;
        this.trackUri = trackUri;
    }

    final String artistName;
    final String trackName;
    final String trackUri;

    @Override
    public String toString() {
        return "TrackInfo{" +
                "artistName='" + artistName + '\'' +
                ", trackName='" + trackName + '\'' +
                ", trackUri='" + trackUri + '\'' +
                '}';
    }

}
