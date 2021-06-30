package com.example.finders;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.auth.User;

import java.util.HashMap;
import java.util.Map;

public class Register extends AppCompatActivity {

    public static final String TAG = "TAG";
    EditText Username, Email, Password, ConfirmPassword;
    Button registerButton;
    TextView loginButton;
    FirebaseAuth fAuth;
    ProgressBar registerProgress;
    FirebaseFirestore fStore;
    String userID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        Username = findViewById(R.id.editTextUsername);
        Email = findViewById(R.id.editTextEmailRegister);
        Password = findViewById(R.id.editTextPasswordRegister);
        ConfirmPassword = findViewById(R.id.editTextConfirmPasswordRegister);
        registerButton = findViewById(R.id.btnRegister);
        loginButton = findViewById(R.id.tvLogin);

        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        registerProgress = findViewById(R.id.progressBarRegister);

        //onclick event for the login button to take the user to the login page
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(getApplicationContext(), Login.class));
            }
        });

        //register the user using Firebase
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final String username = Username.getText().toString().trim();
                final String email = Email.getText().toString().trim();
                String password = Password.getText().toString().trim();
                String confirmPassword = ConfirmPassword.getText().toString().trim();

                //input validation
                if(TextUtils.isEmpty(username)){
                    Username.setError("Username is Required");
                    return;
                }
                if(username.length() < 6){
                    Username.setError("Username must be greater than 6 characters");
                    return;
                }
                if(TextUtils.isEmpty(email)){
                    Email.setError("Email is Required");
                    return;
                }
                if(TextUtils.isEmpty(password)){
                    Password.setError("Password is Required");
                    return;
                }
                if(password.length() < 6){
                    Password.setError("Password must be greater than 6 characters");
                    return;
                }
                if(!confirmPassword.equals(password)){
                    ConfirmPassword.setError("Passwords do not match");
                    return;
                }

                registerProgress.setVisibility(View.VISIBLE);

                //register User to firebase
                fAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if(task.isSuccessful()){
                            Toast.makeText(Register.this, "Registration Successful", Toast.LENGTH_SHORT).show();

                            userID = fAuth.getCurrentUser().getUid();
                            DocumentReference documentReference = fStore.collection("users").document(userID);
                            Map<String, Object> user = new HashMap<>();
                            user.put("Username", username);
                            user.put("Email", email);
                            documentReference.set(user).addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void aVoid) {
                                    Log.d(TAG, "onSuccess: user profile is created for" + userID);
                                }
                            });
                            startActivity(new Intent(getApplicationContext(), Settings.class));
                        }
                        else{
                            Toast.makeText(Register.this, "Error, " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                            registerProgress.setVisibility(View.GONE);
                        }
                    }
                });
            }
        });
    }
}