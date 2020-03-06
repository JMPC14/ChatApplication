package com.example.chatapplication

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.activity_forgot_password.*

class ForgotPasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        supportActionBar?.elevation = 0.toFloat()
        supportActionBar?.title = null

        sendEmailForgotPassword.setOnClickListener {
            val auth = FirebaseAuth.getInstance()
            if (textEmailForgotPassword.text.isNotEmpty()) {
                auth.sendPasswordResetEmail(textEmailForgotPassword.text.toString()).addOnSuccessListener {
                    Toast.makeText(this, "Email sent", Toast.LENGTH_LONG).show()
                }
                    .addOnFailureListener {
                        Toast.makeText(this, "${it.message}", Toast.LENGTH_LONG).show()
                    }
            }
            else {
                Toast.makeText(this, "Please enter an email address", Toast.LENGTH_LONG).show()
            }
        }
    }
}
