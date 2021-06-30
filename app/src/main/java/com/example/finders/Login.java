package com.example.finders;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class Login extends AppCompatActivity {

    EditText Email, Password;
    Button loginButton;
    TextView registerButton;
    FirebaseAuth fAuth;
    ProgressBar loginProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Email = findViewById(R.id.editTextEmail);
        Password = findViewById(R.id.editTextPassword);
        loginProgress = findViewById(R.id.progressBarLogin);
        loginButton = findViewById(R.id.btnLogin);
        registerButton = findViewById(R.id.tvRegister);

        fAuth = FirebaseAuth.getInstance();

        //check if there is a current user and take them straight to the maps activity
        if(fAuth.getCurrentUser() != null){
            startActivity(new Intent(getApplicationContext(), MapsActivity.class));
            finish();
        }

        //register button onclick event to  start the registration activity
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(getApplicationContext(), Register.class));
            }
        });


        //log the user in with email and password using firebase
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String email = Email.getText().toString().trim();
                String password = Password.getText().toString().trim();

                if (TextUtils.isEmpty(email)) {
                    Email.setError("Email is Required");
                    return;
                }
                if (TextUtils.isEmpty(password)) {
                    Password.setError("Password is Required");
                    return;
                }
                loginProgress.setVisibility(View.VISIBLE);

                //authenticate User
                fAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Toast.makeText(Login.this, "Login Successful", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(getApplicationContext(), MapsActivity.class));
                        } else {
                            Toast.makeText(Login.this, "Error, " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                        loginProgress.setVisibility(View.GONE);
                    }
                });
            }
        });

    }

    //exit application when back button is pressed

    @Override
    public void onBackPressed() {
        System.exit(0);
    }
}