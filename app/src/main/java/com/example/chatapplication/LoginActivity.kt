package com.example.chatapplication

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.activity_login.*

class LoginActivity :AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        supportActionBar?.hide()

        goBack.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        loginButton.setOnClickListener {
            val email = textEmailLogin.text.toString()
            val password = textPasswordLogin.text.toString()

            Log.d("Login", "Attempt login with email/pw: $email, $password")

            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener {
                    if (it.isSuccessful) {
                        Log.d("Login", "Login successful")

                        val intent = Intent(this, LatestMessagesActivity::class.java)
                        intent.flags = (Intent.FLAG_ACTIVITY_CLEAR_TASK).or(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        FirebaseManager.user
                    }
                }
                .addOnFailureListener {
                    Log.d("Login", "Login failure")
                    Toast.makeText(this, "Login failed", Toast.LENGTH_LONG).show()
                }
        }
    }
}
