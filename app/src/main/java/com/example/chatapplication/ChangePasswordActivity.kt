package com.example.chatapplication

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.activity_change_password.*

class ChangePasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        supportActionBar?.elevation = 0.toFloat()
        supportActionBar?.title = null

        val email = FirebaseAuth.getInstance().currentUser!!.email

        changePasswordButton.setOnClickListener {
            if (textPasswordChangeOld.text.isEmpty()) {
                Toast.makeText(this, "Please enter your current password", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (textPasswordChangeNew.text.isEmpty() || textPasswordChangeConfirm.text.isEmpty()) {
                Toast.makeText(this, "Please enter a new password", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (textPasswordChangeNew.text.toString() != textPasswordChangeConfirm.text.toString()) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_LONG).show()
                textPasswordChangeNew.text.clear()
                textPasswordChangeConfirm.text.clear()
                return@setOnClickListener
            } else if (textPasswordChangeNew.text.toString() == textPasswordChangeConfirm.text.toString()) {
                FirebaseAuth.getInstance().currentUser!!.updatePassword(textPasswordChangeNew.text.toString())
                    .addOnSuccessListener {
                        Toast.makeText(this, "Password changed", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "${it.message}", Toast.LENGTH_LONG).show()
                        textPasswordChangeNew.text.clear()
                        textPasswordChangeConfirm.text.clear()
                    }
            }
        }
    }
}