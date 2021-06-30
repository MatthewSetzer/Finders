package com.example.finders;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, LocationListener {

    private GoogleMap mMap;

    DocumentReference documentReference, documentReferenceSettings;
    FirebaseFirestore fStore;
    String userID;
    FirebaseAuth fAuth;

    private static final String TAG = "MainActivityTAG";
    private boolean mLocationPermissionGranted = false;
    public static final int ERROR_DIALOG_REQUEST = 9001;
    public static final int PERMISSIONS_REQUEST_ENABLE_GPS = 9002;
    public static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 9003;

    public static final double CURRENT_FUEL_PRICE = 16.91;
    public static final double AVG_LKM = 8;

    public String[] favouriteLandmarks;

    Spinner spType;
    Button btnLocate, btnDirections, btnDetails, btnCompass;
    ImageButton btnSettings;

    double currentLatitude;
    double currentLongitude;
    String currentMarkerDetails;

    double destinationLatitude;
    double destinationLongitude;

    public MarkerOptions pointA, pointB;

    public LocationManager locationManager;
    public FusedLocationProviderClient fusedLocationProviderClient;
    public Location lastKnownLocation;
    public Criteria criteria;
    public String bestProvider;
    public long pressedTime;

    private final LatLng defaultLocation = new LatLng(-33.8523341, 151.2106085);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        userID = Objects.requireNonNull(fAuth.getCurrentUser()).getUid();
        documentReference = fStore.collection("users").document(userID).collection("Landmarks").document("Favourites");

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used
        //---------------------------------------------------------------------------------------------------------------------------------//
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);

        spType = findViewById(R.id.sp_type);
        btnLocate = findViewById(R.id.btnLocate);
        btnDirections = findViewById(R.id.btnDirect);
        btnDetails = findViewById(R.id.btnDetails);
        btnSettings = findViewById(R.id.btnSettings);
        btnCompass = findViewById(R.id.btnCompass);

        //calling the methods to obtain the users location, favourite landmarks and filter accordingly
        getDeviceLocation();
        getFavouriteLandmarks();

        //Directions onclick event to get directions and details to the marker selected by the user
        //---------------------------------------------------------------------------------------------------------------------------------//
        btnDirections.setOnClickListener(view -> {
            pointB = new MarkerOptions().position(new LatLng(destinationLatitude, destinationLongitude));
            mMap.addMarker(pointA);
            String url = getDirectionsUrl(pointA.getPosition(), pointB.getPosition(), "driving");
            Log.d("DirectionsURL", url);

            TaskRequestDirections taskRequestDirections = new TaskRequestDirections();
            taskRequestDirections.execute(url);

            TaskRequestDirectionsDetails taskRequestDirectionsDetails = new TaskRequestDirectionsDetails();
            taskRequestDirectionsDetails.execute(url);
        });

        //Onclick listener for the settings button that takes the user to the settings activity
        //---------------------------------------------------------------------------------------------------------------------------------//
        btnSettings.setOnClickListener(view -> {
            Intent settings = new Intent(MapsActivity.this, Settings.class);
            startActivity(settings);
        });

        //OnClick listener for the details button to request Google Maps API details regarding the current marker selected and display in an Alert
        //---------------------------------------------------------------------------------------------------------------------------------//
        btnDetails.setOnClickListener(view -> {
            if (currentMarkerDetails == null) {
                Toast.makeText(this, "No marker selected", Toast.LENGTH_SHORT).show();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
                builder.setMessage(currentMarkerDetails)
                        .setPositiveButton("Close", (dialogInterface, i) -> dialogInterface.dismiss());
                AlertDialog detailsAlert = builder.create();
                detailsAlert.setTitle("Location Details");
                detailsAlert.show();
            }
        });

        //OnClick listener compass button to take user to the compass activity
        //---------------------------------------------------------------------------------------------------------------------------------//
        btnCompass.setOnClickListener(view -> {
            Intent compass = new Intent(MapsActivity.this, Compass.class);
            startActivity(compass);
        });
    }

    //---------------------------------------------------------------------------------------------------------------------------------//

    //onMapReady override method to get the device location and initialize the map
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        getDeviceLocation();
    }

    //when the application resumes, check the services and permissions before acquiring the users location
    @Override
    protected void onResume() {
        super.onResume();
        if (checkMapServices()) {
            if (mLocationPermissionGranted) {
                getDeviceLocation();
            } else {
                getLocationPermission();
            }
        }
    }

    //boolean method to check if the google play services and that GPS is enabled
    private boolean checkMapServices() {
        if (checkServices()) {
            return checkGPS();
        }
        return false;
    }

    //boolean method to check if GPS is enabled and if not display and alert message
    public boolean checkGPS() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();
            return false;
        }
        return true;
    }

    //method to check if the back button is pressed twice within quick succession and close the application
    @Override
    public void onBackPressed() {
        if (pressedTime + 2000 > System.currentTimeMillis()) {
            super.onBackPressed();
            finish();
            System.exit(0);
        } else {
            Toast.makeText(getBaseContext(), "Press back again to exit", Toast.LENGTH_SHORT).show();
        }
        pressedTime = System.currentTimeMillis();
    }

    //method to retrieve the users saved landmarks from the database and display them in the spinner
    //as well as create the URL for the API call and parser tasks to display the selected landmarks on the map when the user selects the locate button
    //---------------------------------------------------------------------------------------------------------------------------------//
    public void getFavouriteLandmarks() {
        //list of landmark types for use in the url
        String[] landmarkTypeList = {"airport", "bank", "campground", "tourist_attraction", "university", "museum", "park", "bus_station", "church", "courthouse", "hospital", "train_station", "stadium"};
        documentReference.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                assert document != null;
                if (document.exists()) {

                    List<String> list = new ArrayList<>();
                    Map<String, Object> map = document.getData();

                    //adding the key value from the users favourite items from Firestore to the array list
                    if (map != null) {

                        for (Map.Entry<String, Object> entry : map.entrySet()) {
                            list.add(entry.getKey());
                        }

                        favouriteLandmarks = new String[list.size()];
                        list.toArray(favouriteLandmarks);

                        //adapting the users favourite landmarks to the spinner view
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(MapsActivity.this, R.layout.spinner_item, favouriteLandmarks);
                        spType.setAdapter(adapter);

                        //on click event to create a URL for Google Maps API and executing the Parser Methods to display the places on the map.
                        btnLocate.setOnClickListener(view -> {
                            String item = spType.getSelectedItem().toString();
                            String itemIndex = document.getString(item);

                            assert itemIndex != null;
                            String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?" + //URL
                                    "location=" + currentLatitude + "," + currentLongitude + //using users current latitude and longitude
                                    "&radius=5000" + //Nearby radius of 5km
                                    "&type=" + landmarkTypeList[Integer.parseInt(itemIndex)] + //using the saved index of the users stored landmarks that corresponds to the landmarkTypeList
                                    "&sensor=true" + //sensor
                                    "&key=" + getResources().getString(R.string.google_api_key);
                            Log.d("mapURL", url);

                            //Calling the TaskRequestPlaces method to download the URL in the background
                            new TaskRequestPlaces().execute(url);
                        });
                    }
                }
            }
        });
    }

    //Async task to parse JSON and add the places to the map
    //---------------------------------------------------------------------------------------------------------------------------------//
    private class ParserTaskPlaces extends AsyncTask<String, Void, List<HashMap<String, String>>> {
        @Override
        protected List<HashMap<String, String>> doInBackground(String... strings) {
            //calling the JsonParser class to parse the JSON in the correct format
            JsonParser jsonParser = new JsonParser();
            List<HashMap<String, String>> mapList = null;

            JSONObject objectMap;
            try {
                objectMap = new JSONObject((strings[0]));
                mapList = jsonParser.parseResult(objectMap);

            } catch (JSONException e) {
                e.printStackTrace();
            }
            return mapList;
        }

        @Override
        protected void onPostExecute(List<HashMap<String, String>> hashMaps) {
            mMap.clear();
            for (int i = 0; i < hashMaps.size(); i++) {
                HashMap<String, String> hashMapList = hashMaps.get(i);


                double lat = Double.parseDouble(Objects.requireNonNull(hashMapList.get("lat")));
                double lng = Double.parseDouble(Objects.requireNonNull(hashMapList.get("lng")));
                String name = hashMapList.get("name");
                String placeId = hashMapList.get("place_id");

                //adding the place markers to the map based in lat,lng,name and placeId variables
                try {
                    LatLng latLng = new LatLng(lat, lng);
                    MarkerOptions options = new MarkerOptions();
                    options.position(latLng);
                    options.title(name);
                    options.snippet(placeId);
                    mMap.addMarker(options);

                } catch (Exception e) {
                    e.printStackTrace();
                }

                //on marker click listener to construct a URL and execute to obtain the details of the current marker
                mMap.setOnMarkerClickListener(marker -> {
                    destinationLatitude = marker.getPosition().latitude;
                    destinationLongitude = marker.getPosition().longitude;
                    String currentPlaceId = marker.getSnippet();
                    String detailsUrl = "https://maps.googleapis.com/maps/api/place/details/json?" +
                            "place_id=" + currentPlaceId + //using the place ID snippet to obtain details of that location
                            "&fields=name,formatted_address,rating,website" +
                            "&key=" + getResources().getString(R.string.google_api_key);

                    Log.d("detailsURL", detailsUrl);
                    //executing the TaskRequestDetails method to download the URL data in the background
                    new TaskRequestDetails().execute(detailsUrl);
                    return false;
                });
            }
        }
    }

    //Async Task to parse the JSON for the location details
    //---------------------------------------------------------------------------------------------------------------------------------//
    private class ParserTaskDetails extends AsyncTask<String, Void, JSONObject> {
        @Override
        protected JSONObject doInBackground(String... strings) {
            JSONObject objectDetails = null;
            try {
                objectDetails = new JSONObject((strings[0]));

            } catch (JSONException e) {
                e.printStackTrace();
            }
            return objectDetails;
        }

        @Override
        protected void onPostExecute(JSONObject hashMap) {
            try {
                //getting the specified JSON objects and strings
                JSONObject object = hashMap.getJSONObject("result");
                String formattedAddress = object.getString("formatted_address");
                String website = object.getString("website");
                double rating = object.getDouble("rating");

                //adding the API details to the currentMarkerDetails global variable to be used once the details button is clicked
                currentMarkerDetails = "Address: " + formattedAddress + "\nWebsite: " + website + "\nRating: " + rating;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    //Async Task to parse the JSON for the directions
    //---------------------------------------------------------------------------------------------------------------------------------//
    private class ParserTaskDirections extends AsyncTask<String, Void, List<List<HashMap<String, String>>>> {
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... strings) {
            JSONObject jsonObject;
            List<List<HashMap<String, String>>> routes;
            routes = null;
            try {
                jsonObject = new JSONObject((strings[0]));
                //using the DataParser class to parse the JSON in the correct format
                DataParser directionsParser = new DataParser();
                routes = directionsParser.parse(jsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return routes;
        }

        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> lists) {
            //get the list route and display it on the map
            ArrayList points;
            PolylineOptions polylineOptions = null;
            for (List<HashMap<String, String>> path : lists) {
                points = new ArrayList();
                polylineOptions = new PolylineOptions();

                //foreach point, obtain the lat and lng variables to construct the route
                for (HashMap<String, String> point : path) {
                    double lat = Double.parseDouble(Objects.requireNonNull(point.get("lat")));
                    double lng = Double.parseDouble(Objects.requireNonNull(point.get("lng")));

                    points.add(new LatLng(lat, lng));
                }

                //adding the polyline to the map
                polylineOptions.addAll(points);
                polylineOptions.width(15);
                polylineOptions.color(Color.rgb(0,173,181));
                polylineOptions.geodesic(true);
            }

            if (polylineOptions != null) {
                mMap.addPolyline(polylineOptions);
            } else {
                Toast.makeText(getApplicationContext(), "Directions not found, click on a marker", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //Async Task to obtain details of the directions provided (Time and Distance)
    //---------------------------------------------------------------------------------------------------------------------------------//
    private class ParserTaskDirectionsDetails extends AsyncTask<String, Void, JSONObject> {
        @Override
        protected JSONObject doInBackground(String... strings) {
            JSONObject objectDetails = null;
            try {
                objectDetails = new JSONObject((strings[0]));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return objectDetails;
        }

        @Override
        protected void onPostExecute(JSONObject hashMap) {
            try {

                //getting the required objects and strings from the JSON data
                JSONArray array = hashMap.getJSONArray("routes");
                JSONArray legs = ((JSONObject) array.get(0)).getJSONArray("legs");

                String distance = (String) ((JSONObject) ((JSONObject) legs.get(0)).get("distance")).get("text");
                String finalTime = (String) ((JSONObject) ((JSONObject) legs.get(0)).get("duration")).get("text");

                Double finalDistanceNumeric = Objects.requireNonNull(NumberFormat.getInstance().parse(distance)).doubleValue();
                String finalDistanceString = finalDistanceNumeric + " km";

                //estimated fuel cost for metric and imperial
                Double estimatedCost= (finalDistanceNumeric/100) * AVG_LKM * CURRENT_FUEL_PRICE;

                //checking the users setting for units
                documentReferenceSettings = fStore.collection("users").document(userID).collection("Landmarks").document("Settings");
                documentReferenceSettings.get().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        final DocumentSnapshot document = task.getResult();
                        assert document != null;
                        if (document.exists()) {
                            Log.d(TAG, "DocumentSnapshot data: " + document.getData());
                            documentReferenceSettings.get().addOnSuccessListener(documentSnapshot -> {
                                String unit = documentSnapshot.getString("Unit");

                                //If the users setting is Imperial then display the distance in miles
                                if (Objects.equals(unit, "Imperial")) {
                                    Double finalDistanceImperial = finalDistanceNumeric * 0.6214;
                                    String finalDistanceStringImperial = String.format("%.2f", finalDistanceImperial) + " miles";

                                    AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
                                    builder.setMessage("Distance: " + finalDistanceStringImperial + "\n" + "Time: " + finalTime + "\n" +
                                            "Estimated Fuel Cost: R" + String.format("%.2f",estimatedCost)) //estimated fuel costs calculated for miles
                                            .setPositiveButton("Close", (dialogInterface, i) -> dialogInterface.dismiss());
                                    AlertDialog detailsAlert = builder.create();
                                    detailsAlert.setTitle("Trip Details");
                                    detailsAlert.show();
                                } else {

                                    //if the users setting is metric then display the distance in km
                                    AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
                                    builder.setMessage("Distance: " + finalDistanceString + "\n" + "Time: " + finalTime + "\n" +
                                            "Estimated Fuel Cost: R" + String.format("%.2f",estimatedCost)) //estimated fuel costs calculated for km
                                            .setPositiveButton("Close", (dialogInterface, i) -> dialogInterface.dismiss());

                                    AlertDialog detailsAlert = builder.create();
                                    detailsAlert.setTitle("Trip Details");
                                    detailsAlert.show();
                                }
                            });
                        } else {
                            Log.d(TAG, "No such document");
                        }
                    } else {
                        Log.d(TAG, "get failed with ", task.getException());
                    }
                });
            } catch (JSONException | ParseException e) {
                e.printStackTrace();
            }
        }
    }

    //method to download the Google Maps API URL(s)
    //---------------------------------------------------------------------------------------------------------------------------------//
    private String downloadUrl(String string) throws IOException {
        URL url = new URL(string);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.connect();
        InputStream stream = connection.getInputStream();
        BufferedReader reader = new BufferedReader((new InputStreamReader(stream)));
        StringBuilder builder = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        String data = builder.toString();
        reader.close();
        return data;
    }

    //method that constructs the directions URL and takes the origin, destination and directionMode as parameters
    //---------------------------------------------------------------------------------------------------------------------------------//
    private String getDirectionsUrl(LatLng origin, LatLng dest, String directionMode) {
        String strOrigin = "origin=" + origin.latitude + "," + origin.longitude;
        String strDest = "destination=" + dest.latitude + "," + dest.longitude;
        String mode = "mode=" + directionMode;
        String parameters = strOrigin + "&" + strDest + "&" + mode;
        String output = "json";
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters + "&key=" + getString(R.string.google_api_key);
        return url;
    }


    //All four Async methods below are called to download the respective URLs in the background and execute the string in the corresponding parser task
    //---------------------------------------------------------------------------------------------------------------------------------//
    private class TaskRequestPlaces extends AsyncTask<String, Integer, String> {
        String data = null;

        @Override
        protected String doInBackground(String... strings) {
            try {
                data = downloadUrl(strings[0]);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return data;
        }

        @Override
        protected void onPostExecute(String s) {
            //Execute parser task
            new ParserTaskPlaces().execute(s);
        }
    }

    private class TaskRequestDetails extends AsyncTask<String, Integer, String> {
        String data = null;

        @Override
        protected String doInBackground(String... strings) {
            try {
                data = downloadUrl(strings[0]);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return data;
        }

        @Override
        protected void onPostExecute(String s) {
            new ParserTaskDetails().execute(s);
        }
    }

    private class TaskRequestDirections extends AsyncTask<String, Void, String> {
        String responseString = null;

        @Override
        protected String doInBackground(String... strings) {

            try {
                responseString = downloadUrl(strings[0]);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return responseString;
        }

        @Override
        protected void onPostExecute(String s) {
            new ParserTaskDirections().execute(s);
        }
    }

    private class TaskRequestDirectionsDetails extends AsyncTask<String, Void, String> {
        String responseString = null;

        @Override
        protected String doInBackground(String... strings) {

            try {
                responseString = downloadUrl(strings[0]);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return responseString;
        }

        @Override
        protected void onPostExecute(String s) {
            new ParserTaskDirectionsDetails().execute(s);
        }
    }

    //method to get the device location and display the current users position on the map
    //---------------------------------------------------------------------------------------------------------------------------------//
    private void getDeviceLocation() {
        try {
            if (mLocationPermissionGranted) {
                Task<Location> locationResult = fusedLocationProviderClient.getLastLocation();
                if (locationResult != null) {
                    locationResult.addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device and add marker.
                            lastKnownLocation = task.getResult();
                            if (lastKnownLocation != null) {
                                currentLatitude = lastKnownLocation.getLatitude();
                                currentLongitude = lastKnownLocation.getLongitude();
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(currentLatitude, currentLongitude), 15));
                                pointA = new MarkerOptions().position(new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude())).title("Your Location");
                                mMap.addMarker(pointA);
                            }
                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            mMap.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(defaultLocation, 15));
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    });
                    //if for any reason the users device resets and there is a null reference to the last known location.
                } else {
                    locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
                    criteria = new Criteria();
                    bestProvider = String.valueOf(locationManager.getBestProvider(criteria, true));
                    locationManager.requestLocationUpdates(bestProvider, 1000, 0, this);
                    Location location = locationManager.getLastKnownLocation(bestProvider);
                    if (location != null) {
                        currentLatitude = location.getLatitude();
                        currentLongitude = location.getLongitude();

                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(currentLatitude, currentLongitude), 15));
                        pointA = new MarkerOptions().position(new LatLng(currentLatitude, currentLongitude)).title("Your Location");
                        mMap.addMarker(pointA);
                    } else {
                        Log.d(TAG, "Current location is null. Using defaults.");
                        mMap.moveCamera(CameraUpdateFactory
                                .newLatLngZoom(defaultLocation, 15));
                        mMap.getUiSettings().setMyLocationButtonEnabled(false);
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage(), e);
        }
    }

    //method to build the no GPS alert message and allow the user to enable it
    //---------------------------------------------------------------------------------------------------------------------------------//
    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("This application requires GPS to work properly, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", (dialog, id) -> {
                    Intent enableGpsIntent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivityForResult(enableGpsIntent, PERMISSIONS_REQUEST_ENABLE_GPS);
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    //method to obtain the location permission
    //---------------------------------------------------------------------------------------------------------------------------------//
    private void getLocationPermission() {
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    //boolean method to check if Google Play services is enabled on the users device
    //---------------------------------------------------------------------------------------------------------------------------------//
    public boolean checkServices() {

        int available = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(MapsActivity.this);
        if (available == ConnectionResult.SUCCESS) {
            //everything is working and the user can make map requests
            Log.d(TAG, "Google Play Services is working");
            return true;
        } else if (GoogleApiAvailability.getInstance().isUserResolvableError(available)) {
            //an error occurred and services are not available
            Log.d(TAG, "An error occurred");
            Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(MapsActivity.this, available, ERROR_DIALOG_REQUEST);
            dialog.show();
        } else {
            Toast.makeText(this, "You can't make map requests", Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    //once the permissions have been requested then set the global variable mLocationPermissionGranted to true
    //---------------------------------------------------------------------------------------------------------------------------------//
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        if (requestCode == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mLocationPermissionGranted = true;
            }
        }
    }

    //ask for permission again if the user has not giving the correct permissions, get the device location if the permissions have been granted
    //---------------------------------------------------------------------------------------------------------------------------------//
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: called.");
        if (requestCode == PERMISSIONS_REQUEST_ENABLE_GPS) {
            if (mLocationPermissionGranted) {
                getDeviceLocation();
            } else {
                getLocationPermission();
            }
        }
    }

    //onLocationChanged method that calls getDeviceLocation again if a change in location has been detected
    //---------------------------------------------------------------------------------------------------------------------------------//
    @Override
    public void onLocationChanged(Location location) {
        currentLatitude = location.getLatitude();
        currentLongitude = location.getLongitude();
        getDeviceLocation();
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {

    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    //---------------------------------------------------------------------------------------------------------------------------------//
}