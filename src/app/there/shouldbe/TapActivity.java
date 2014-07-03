package app.there.shouldbe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.maps.MapActivity;
import com.parse.FindCallback;
import com.parse.Parse;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;

public class TapActivity extends MapActivity implements 
	GooglePlayServicesClient.ConnectionCallbacks, 
	GooglePlayServicesClient.OnConnectionFailedListener {
	
	private GoogleMap mMap;
	private static final LatLng GDC = new LatLng(30.286336,-97.736693);  //Yay UT
	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
	private LocationClient mLocationClient;
	private Location mCurrentLocation;
	private LatLng mCurrentLatLng;
	private ProgressDialog pDialog;
	
	private HashMap<Marker, Integer> markerLikes = new HashMap<Marker, Integer>(); //TODO replace with server queries
	
	private HashMap<Marker, ViewGroup> markers2Windows = new HashMap<Marker, ViewGroup>();
	private HashMap<Marker, String> markers2Statuses = new HashMap<Marker, String>();
	
	//info window global elements
	private ViewGroup infoWindow;
    private Button likeButton;
    private OnInfoWindowElemTouchListener likeButtonListener;
    //private Boolean boolEmptyInfoWindow = true;
    
    
	/*
	 * TODO = reimplement marker retention
	 */
	private ArrayList<Marker> markerArray = new ArrayList<Marker>();
	private boolean posted;
    
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tap_location);
        
        // Make sure we're running on Honeycomb or higher to use ActionBar APIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        Log.d("TapOnCreate", "new map activity");
        posted = false;
        
        // Setup Google Map
        mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
        mMap.setMyLocationEnabled(true);
        final MapWrapperLayout mapWrapperLayout = (MapWrapperLayout)findViewById(R.id.map_relative_layout);
        mLocationClient = new LocationClient(this, this, this);
        setUpMapIfNeeded();  // Check to make sure map loads
        mMap.getUiSettings().setZoomControlsEnabled(false);   // Remove +/- zoom controls since pinching is enabled
        
        
        
        // Marker info windows = empty and post
        final ViewGroup emptyInfoWindow = (ViewGroup)getLayoutInflater().inflate(R.layout.map_info_window_empty, null);
        infoWindow = (ViewGroup)getLayoutInflater().inflate(R.layout.map_info_window, null); //initiate infowindow for after pos
        
        //Create new marker on map click
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng point) { 
                Marker marker = null;
                Boolean boolEmptyInfoWindow = true;
                
                marker = mMap.addMarker(new MarkerOptions().position(point)
                	.icon(BitmapDescriptorFactory.fromResource(R.drawable.shouldbepin))
                	.title("There should be:")  //not used
                	);
                zoomToLatLngLocation(point);  //zoom can be severely disorienting to user
                
                //new marker is presented with simple add ShouldBe window
                markers2Windows.put(marker, emptyInfoWindow);

                
//                //check to see if should remove empty marker
                if (!markerArray.isEmpty()){
                	if(markers2Statuses.get(markerArray.get(markerArray.size()-1)) == null){
                		markerArray.get(markerArray.size()-1).remove();
                	}
                }
                markerArray.add(marker);
                
                Log.d("**markerCreated", "new marker on map click");
//                TextView likeCount = (TextView)infoWindow.findViewById(R.id.like_count);
//                likeCount.setText("0"); //TODO set with like count from Parse
                marker.showInfoWindow(); 
            }

        });
        
        likeButton = (Button)infoWindow.findViewById(R.id.likeButton);
    	likeButtonListener = new OnInfoWindowElemTouchListener(likeButton,
                getResources().getDrawable(R.drawable.like),
                getResources().getDrawable(R.drawable.like2)) {
            @Override
            protected void onClickConfirmed(View v, Marker marker) {
                // *** TODO register click as a "like" counting towards the ShouldBe
            	TextView likeCount = (TextView)infoWindow.findViewById(R.id.like_count);
            	likeCount.setText(String.valueOf("You like this"));
            	marker.showInfoWindow();
                //Toast.makeText(TapActivity.this, marker.getTitle() + "'s button clicked! " + markerLikes.get(marker), Toast.LENGTH_SHORT).show();
            }
        }; 
        
        /* 
         * Setup pin infowindow
         */
        // MapWrapperLayout initialization
        // 39 - default marker height
        // 20 - offset between the default InfoWindow bottom edge and it's content bottom edge 
        mapWrapperLayout.init(mMap, getPixelsFromDp(this, 39 + 20)); 

        /*
         * what ShouldBe map_empty button click listener
         */
//        this.whatShouldBe = (Button)emptyInfoWindow.findViewById(R.id.shouldBeButton);
//        infoButtonListener = new OnInfoWindowElemTouchListener(whatShouldBe,
//              getResources().getDrawable(R.drawable.official_background),
//              getResources().getDrawable(R.drawable.official_background1)) {
//			@Override
//			protected void onClickConfirmed(View v, Marker marker) {
//				Log.d("*infoButtonListener", "onClickConfirmed");
//				Intent intent = new Intent(TapActivity.this, WhatShouldBeActivity.class);
//				startActivityForResult(intent, 1);
//			}
//		};
		
		mMap.setOnInfoWindowClickListener(new OnInfoWindowClickListener() {
                    public void onInfoWindowClick(Marker marker){
                    	if(markers2Statuses.get(marker) == null){
	                    	Log.d("**infoWINDOWListener", "onInfoWindowClick");
	                    	LatLng markerPos = marker.getPosition();
	        				Intent intent = new Intent(TapActivity.this, WhatShouldBeActivity.class);
	        				intent.putExtra("lat", markerPos.latitude);
	        				intent.putExtra("long", markerPos.longitude);
	        				startActivityForResult(intent, 1);
                    	}
                    }
                  });
		
		
		/*
		 * called on .showInfoWindow()
		 */
        mMap.setInfoWindowAdapter(new InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return null;
            }

            private void giveInfoWindowTweet(Marker marker) {
            	
            	TextView postedTweet = (TextView)infoWindow.findViewById(R.id.posted_tweet);
            	postedTweet.setText(String.valueOf(markers2Statuses.get(marker)));
                likeButtonListener.setMarker(marker);
                likeButton.setOnTouchListener(likeButtonListener);
                
            	markers2Windows.put(marker, infoWindow);  //update to new layout
			}

			@Override
            public View getInfoContents(Marker marker) {
                //TODO replace with call to Parse/ check against Parse having filled contents
            	if(markers2Statuses.get(marker) == null){
            		//infoButtonListener.setMarker(marker);
            		if (markerArray.get(markerArray.size()-1) != marker){
            			markerArray.add(marker);
            			Log.d("**infoWindow", "added this marker");
            		}
            		Log.d("**infoWindow", "sans status");
            	}
            	else{
            		//if the marker has been assigned a tweet
            		giveInfoWindowTweet(marker);
            		Log.d("**infoWindow", "giveInfoWindowTweet");
            	}
            	Log.d("**infoWindow", "getInfoContents"); 
                mapWrapperLayout.setMarkerWithInfoWindow(marker, markers2Windows.get(marker));
                return markers2Windows.get(marker); //return infowindow associated with this marker
            }
        });  
        
        // ========================
        // ======== Parse =========
        // ========================
        Parse.initialize(this, "3wJJsTSZovcJZreXDjYVeJi3e1AOqAZEA8e2S860", "y1SZ9RtY8wuv9sOaXTIHrapLK5uk6LrehEEYylZd");
        populateMarkersFromDatabase();

    }


    
    public void shouldBeUpdate (String status) {
    	
    	posted = true; //if the user has go to tweet activity
    	
    	//if last struct og the temp marker array != null
		if (markerArray.get(markerArray.size()-1) != null){
			//Make new marker and display updated infowindow
            Marker marker = markerArray.get(markerArray.size()-1);
            Log.d("**sbUpdate", "marker not null");
            LatLng markerPos = marker.getPosition();
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(markerPos, 15)); 
            
            // save marker object, eventually to server
            ParseObject saveMarker = new ParseObject("Marker");
            saveMarker.put("lat", markerPos.latitude);
            saveMarker.put("long", markerPos.longitude);
            saveMarker.put("shouldbeText", status);
            saveMarker.saveEventually();
            
            markers2Statuses.put(marker, status);
            marker.showInfoWindow();
            zoomToLatLngLocation(marker.getPosition());
            Log.d("**sbUpdate", "showInfoWindow called");
		}
	}
    

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.home, menu);

		return true;
	}
    
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        if(item.getItemId() == R.id.settings){
			startActivity(new Intent(this, Settings.class)); 
			return true;
		}
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * Method to check if the map is null, if so, setup the map
     */
    private void setUpMapIfNeeded() {
        if (mMap == null) {
            mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
        }
    }
    
    /**
     * Converts dp to px
     * @param context	this context
     * @param dp	dp to convert
     * @return	returns dp in pixels
     */
    public static int getPixelsFromDp(Context context, float dp) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int)(dp * scale + 0.5f);
    }

    @Override
    protected void onStart() {
    	super.onStart();
    	setUpMapIfNeeded();
    	mLocationClient.connect();
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	setUpMapIfNeeded();
    	mLocationClient.connect();
    	markerArray.clear();
    }
    
    
    @Override 
    protected void onStop() {
    	
        //mMap = null;
    	super.onStop();
    	if (mLocationClient != null)
    		mLocationClient.disconnect();
    }
    
//    @Override
//    public void onDestroyView() {
//        SupportMapFragment f = (SupportMapFragment) getFragmentManager()
//                .findFragmentById(id);
//        if (f != null) {
//            try {
//                getFragmentManager().beginTransaction().remove(f).commit();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//        if (mMap != null) {
//            List<Marker> temp = mMap.getMarkers();
//            if (temp != null) {
//                for (Marker marker : temp) {
//                    marker.remove();
//                    marker = null;
//                }
//            }
//        }
//        mMap = null;
//        super.onDestroyView();
//
//    }

    private void zoomToUserLocation() {
    	mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mCurrentLatLng, 15));
    }
    
    private void zoomToLatLngLocation(LatLng point) {
    	mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 15));
    }

	@Override
    protected boolean isRouteDisplayed() {
        return false;
    }
	
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Decide what to do based on the original request code
        switch (requestCode) {
            case CONNECTION_FAILURE_RESOLUTION_REQUEST :
                switch (resultCode) {
                    case Activity.RESULT_OK :
                    break;
                    default:
                    	break;
                }
            case 1:
            	switch (resultCode) {
            	case Activity.RESULT_OK:
            		if (!data.getStringExtra("status").isEmpty()) {
                    	String shouldBeText = data.getStringExtra("status");
                    	//Log.d("**onActivityResult", "**status text = " + shouldBeText);
                    	shouldBeUpdate(shouldBeText);
                	}
            		break;
            	default: 
            		break;
            	}
            	
        }
     }
	
	@Override
	public void onConnected(Bundle dataBundle) {
        // Display the connection status
        Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();
        
        if (!posted){
	        mCurrentLocation = mLocationClient.getLastLocation();
	        mCurrentLatLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
	        zoomToUserLocation();
        }

    }

	@Override
	public void onConnectionFailed(ConnectionResult arg0) {
		Toast.makeText(this, "Connection Error!", Toast.LENGTH_LONG).show();
		
	}

	@Override
	public void onDisconnected() {
		Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
		
	}
	
	public void populateMarkersFromDatabase() {
		// check if map is null since called from onCreate
		if (mMap != null) {
			ParseQuery<ParseObject> query = ParseQuery.getQuery("Marker");
			query.findInBackground(new FindCallback<ParseObject>() {

				@Override
				public void done(List<ParseObject> parseObjects, ParseException e) {
					if (e == null) {
						for (ParseObject p : parseObjects) {
							Marker m = null;
							LatLng pos = new LatLng((Double) p.get("lat"), (Double) p.get("long"));
							m = mMap.addMarker(new MarkerOptions().position(pos)
				                	.icon(BitmapDescriptorFactory.fromResource(R.drawable.shouldbepin))
				                	.title((String)p.get("shouldbeText"))  //not used
				                	);
							markers2Statuses.put(m, (String)p.get("shouldbeText"));
					        TextView likeCount = (TextView)infoWindow.findViewById(R.id.like_count);
					        likeCount.setText(String.valueOf(markerLikes.get(m)));  //TODO replace with call to Parse
						}
					}
					else {
						e.printStackTrace();
					}
				}
				
			});
		}
	}
	
}