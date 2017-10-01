package com.pajtek.spotifind;

import android.graphics.Point;
import android.util.Log;
import android.view.View;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import pl.bclogic.pulsator4droid.library.PulsatorLayout;

/**
 * Created by antonhagermalm on 2017-10-01.
 */

public class PulseMarkerHandler {
    private Map<String, LatLng> map;
    private GoogleMap mMap;
    private List<PulsatorLayout> pulsatorLayoutArrayList;

    public PulseMarkerHandler(Map<String, LatLng> map, GoogleMap mMap, List<PulsatorLayout> pulsatorLayoutArrayList) {
        this.map = map;
        this.mMap = mMap;
        this.pulsatorLayoutArrayList = pulsatorLayoutArrayList;
    }

    public void update() {
        Iterator iterator = map.entrySet().iterator();
        int index = 0;
        while (iterator.hasNext()) {
            Map.Entry pair = (Map.Entry) iterator.next();
            LatLng position = ((LatLng) pair.getValue());
            if (index < 12) {
                Point point = mMap.getProjection().toScreenLocation(position);
                pulsatorLayoutArrayList.get(index).setX(point.x - 150);
                pulsatorLayoutArrayList.get(index).setY(point.y - 150);
                pulsatorLayoutArrayList.get(index).setVisibility(View.VISIBLE);
                pulsatorLayoutArrayList.get(index).setAlpha(0.3f);
                pulsatorLayoutArrayList.get(index).setCount(4);
                pulsatorLayoutArrayList.get(index).setDuration(7000);
                pulsatorLayoutArrayList.get(index).start();
            } else {
                break;
            }
            index++;
        }


        for (int i = index; i < 12; i++) {
            pulsatorLayoutArrayList.get(i).setVisibility(View.INVISIBLE);
        }
    }

}
