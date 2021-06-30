package com.example.finders;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Settings extends AppCompatActivity {
    private static final String TAG = "SETTINGS: ";
    ListView listViewLandmarks;
    ArrayAdapter<String> adapter;
    FirebaseFirestore fStore;
    SwitchCompat switchUnits;
    Button btnBack, btnLogout;
    String userID;
    DocumentReference documentReference, documentReferenceSettings;
    FirebaseAuth fAuth;
    String[] landmarks = {"Airport", "Bank", "Campground", "Tourist Attraction", "University", "Museum", "Park", "Bus Station", "Church", "Courthouse", "Hospital", "Train Station", "Stadium"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        switchUnits = findViewById(R.id.switchUnits);
        btnBack = findViewById(R.id.btnBack);
        btnLogout = findViewById(R.id.btnLogout);
        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        userID = Objects.requireNonNull(fAuth.getCurrentUser()).getUid();

        documentReference = fStore.collection("users").document(userID).collection("Landmarks").document("Favourites");
        documentReferenceSettings = fStore.collection("users").document(userID).collection("Landmarks").document("Settings");

        //adapt the list view to the landmarks array
        listViewLandmarks = findViewById(R.id.lv_landmarkList);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, landmarks);
        listViewLandmarks.setAdapter(adapter);

        //get the users current setting and set teh switch to the appropriate position
        documentReferenceSettings.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                final DocumentSnapshot document = task.getResult();
                assert document != null;
                if (document.exists()) {
                    Log.d(TAG, "DocumentSnapshot data: " + document.getData());
                    documentReferenceSettings.get().addOnSuccessListener(documentSnapshot -> {
                        String unit = documentSnapshot.getString("Unit");
                        if (Objects.equals(unit, "Imperial")) {
                            switchUnits.setChecked(true);
                        }
                    });
                } else {
                    Log.d(TAG, "No such document");
                }
            } else {
                Log.d(TAG, "get failed with ", task.getException());
            }
        });

        //Switch on changed listener to change the current setting in firebase to the users selection
        switchUnits.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            Map<String, Object> currentSetting = new HashMap<>();
            if (isChecked) {
                currentSetting.put("Unit", "Imperial");
            } else {
                currentSetting.put("Unit", "Metric");
            }
            documentReferenceSettings.set(currentSetting);
        });

        //back button onclick listener to take the user back to the maps activity
        btnBack.setOnClickListener(view -> {
            Intent mapRefresh = new Intent(Settings.this, MapsActivity.class);
            startActivity(mapRefresh);
        });

        //logout button onclick listener to logout the user and return to the login page
        btnLogout.setOnClickListener(view -> {
            fAuth.signOut();
            Intent login = new Intent(Settings.this, Login.class);
            startActivity(login);
        });
    }

    //inflate the custom main_menu xml onto the local options menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    //on options selected method to add the checked favourite landmarks to firebase
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Map<String, Object> savedLandmarks = new HashMap<>();
        int id = item.getItemId();
        if (id == R.id.item_save) {
            for (int i = 0; i < listViewLandmarks.getCount(); i++) {
                 if (listViewLandmarks.isItemChecked(i)) {
                    savedLandmarks.put((String) listViewLandmarks.getItemAtPosition(i), "" + i);
                }
            }
        }
        //at least one landmark needs to be selected in order to save the data to firebase
        if(savedLandmarks.size() == 0){
            Toast.makeText(Settings.this, "Select more than one landmark item to save a new favourites list", Toast.LENGTH_LONG).show();
        }
        documentReference.set(savedLandmarks).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Landmark types saved successfully for: " + userID);
            Intent mapRefresh = new Intent(Settings.this, MapsActivity.class);
            startActivity(mapRefresh);
        });
        return super.onOptionsItemSelected(item);
    }
}