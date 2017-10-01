package com.pajtek.spotifind;

/**
 * Created by Simon on 2017-09-30.
 */

public class TrackInfo {

    public TrackInfo(String artistName, String trackName, String trackUri, String albumCoverWebUrl) {
        this.artistName = artistName;
        this.trackName = trackName;
        this.trackUri = trackUri;
        this.albumCoverWebUrl = albumCoverWebUrl;
    }

    final String artistName;
    final String trackName;
    final String trackUri;
    final String albumCoverWebUrl;

    @Override
    public String toString() {
        return "TrackInfo{" +
                "artistName='" + artistName + '\'' +
                ", trackName='" + trackName + '\'' +
                ", trackUri='" + trackUri + '\'' +
                '}';
    }

}
