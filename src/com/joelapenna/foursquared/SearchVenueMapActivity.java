/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquared;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.OverlayItem;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.maps.VenueItemizedOverlay;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

/**
 * @author Joe LaPenna (joe@joelapenna.com)
 */
public class SearchVenueMapActivity extends MapActivity {
    public static final String TAG = "SearchVenueMapActivity";
    public static final boolean DEBUG = Foursquared.DEBUG;

    private Venue mTappedVenue;

    private MapView mMapView;
    private MapController mMapController;
    private ArrayList<VenueItemizedOverlay> mVenuesGroupOverlays = new ArrayList<VenueItemizedOverlay>();
    private MyLocationOverlay mMyLocationOverlay;
    private Observer mSearchResultsObserver;
    private Button mVenueButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_venue_map_activity);

        mVenueButton = (Button)findViewById(R.id.venueButton);
        mVenueButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DEBUG) Log.d(TAG, "firing venue activity for venue");
                Intent intent = new Intent(SearchVenueMapActivity.this, VenueActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra("venue", mTappedVenue);
                startActivity(intent);
            }
        });

        initMap();

        mSearchResultsObserver = new Observer() {
            @Override
            public void update(Observable observable, Object data) {
                if (DEBUG) Log.d(TAG, "Observed search results change.");
                clearMap();
                loadSearchResults(SearchVenueActivity.searchResultsObservable.getSearchResults());
                recenterMap();
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.d(TAG, "onResume()");
        mMyLocationOverlay.enableMyLocation();
        // mMyLocationOverlay.enableCompass(); // Disabled due to a sdk 1.5 emulator bug

        clearMap();
        loadSearchResults(SearchVenueActivity.searchResultsObservable.getSearchResults());
        recenterMap();

        SearchVenueActivity.searchResultsObservable.addObserver(mSearchResultsObserver);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.d(TAG, "onPause()");
        mMyLocationOverlay.disableMyLocation();
        mMyLocationOverlay.disableCompass();
        SearchVenueActivity.searchResultsObservable.deleteObserver(mSearchResultsObserver);
    }

    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }

    private void initMap() {
        mMapView = (MapView)findViewById(R.id.mapView);
        mMapView.setBuiltInZoomControls(true);
        mMapController = mMapView.getController();

        mMyLocationOverlay = new MyLocationOverlay(this, mMapView);
        mMapView.getOverlays().add(mMyLocationOverlay);
        mMyLocationOverlay.runOnFirstFix(new Runnable() {
            public void run() {
                if (DEBUG) Log.d(TAG, "runOnFirstFix()");
                mMapView.getController().animateTo(mMyLocationOverlay.getMyLocation());
                mMapView.getController().setZoom(16);
            }
        });
    }

    private void loadSearchResults(Group searchResults) {
        if (searchResults == null) {
            if (DEBUG) Log.d(TAG, "no search results. Not loading.");
            return;
        }
        if (DEBUG) Log.d(TAG, "Loading search results");

        final int groupCount = searchResults.size();
        for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
            Group group = (Group)searchResults.get(groupIndex);

            // One VenueItemizedOverlay per group!
            VenueItemizedOverlay mappableVenuesOverlay = createMappableVenuesOverlay(group);

            if (mappableVenuesOverlay != null) {
                if (DEBUG) Log.d(TAG, "adding a map view venue overlay.");
                mVenuesGroupOverlays.add(mappableVenuesOverlay);
            }
        }
        // Only add the list of venue group overlays if it contains any overlays.
        if (mVenuesGroupOverlays.size() > 0) {
            mMapView.getOverlays().addAll(mVenuesGroupOverlays);
        }
    }

    private void clearMap() {
        if (DEBUG) Log.d(TAG, "clearMap()");
        mVenuesGroupOverlays.clear();
        mMapView.getOverlays().clear();
        mMapView.getOverlays().add(mMyLocationOverlay);
        mMapView.postInvalidate();
    }

    /**
     * Create an overlay that contains a specific group's list of mappable venues.
     *
     * @param group
     * @return
     */
    private VenueItemizedOverlay createMappableVenuesOverlay(Group group) {
        Group mappableVenues = new Group();
        mappableVenues.setType(group.getType());
        if (DEBUG) Log.d(TAG, "Adding items in group: " + group.getType());

        final int venueCount = group.size();
        for (int venueIndex = 0; venueIndex < venueCount; venueIndex++) {
            Venue venue = (Venue)group.get(venueIndex);
            if (isVenueMappable(venue)) {
                if (DEBUG) Log.d(TAG, "adding venue: " + venue.getVenuename());
                mappableVenues.add(venue);
            }
        }
        if (mappableVenues.size() > 0) {
            VenueItemizedOverlay mappableVenuesOverlay = new VenueItemizedOverlayWithButton( //
                    this.getResources().getDrawable(R.drawable.reddot), //
                    this.getResources().getDrawable(R.drawable.bluedot));
            mappableVenuesOverlay.setGroup(mappableVenues);
            return mappableVenuesOverlay;
        } else {
            return null;
        }
    }

    private boolean isVenueMappable(Venue venue) {
        if ((venue.getGeolat() == null //
                || venue.getGeolong() == null) //
                || venue.getGeolat().equals("0") //
                || venue.getGeolong().equals("0")) {
            return false;
        }
        return true;
    }

    private void recenterMap() {
        GeoPoint center = mMyLocationOverlay.getMyLocation();
        if (center != null) {
            if (DEBUG) Log.d(TAG, "updateMap via location overlay");
            mMapController.animateTo(center);
            mMapController.setZoom(16);
            return;
        }
        if (DEBUG) Log.d(TAG, "Could not re-center no location or venue overlay.");
    }

    private class VenueItemizedOverlayWithButton extends VenueItemizedOverlay {
        public static final String TAG = "VenueItemizedOverlayWithToast";
        public static final boolean DEBUG = Foursquared.DEBUG;

        private Drawable mBeenThereMarker;

        public VenueItemizedOverlayWithButton(Drawable defaultMarker, Drawable beenThereMarker) {
            super(defaultMarker);
            mBeenThereMarker = boundCenterBottom(beenThereMarker);
        }

        @Override
        public OverlayItem createItem(int i) {
            VenueOverlayItem item = (VenueOverlayItem)super.createItem(i);
            if (item.getVenue().beenhereMe()) {
                if (DEBUG) Log.d(TAG, "using the beenThereMarker for: " + item.getVenue());
                item.setMarker(mBeenThereMarker);
            }
            return item;
        }

        @Override
        public boolean onTap(GeoPoint p, MapView mapView) {
            if (DEBUG) Log.d(TAG, "onTap: " + p);
            mVenueButton.setVisibility(View.GONE);
            return super.onTap(p, mapView);
        }

        @Override
        protected boolean onTap(int i) {
            if (DEBUG) Log.d(TAG, "onTap: " + i);
            VenueOverlayItem item = (VenueOverlayItem)getItem(i);
            mTappedVenue = item.getVenue();
            mVenueButton.setText(item.getVenue().getVenuename());
            mVenueButton.setVisibility(View.VISIBLE);
            return super.onTap(i);
        }
    }
}