package com.example.chatapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.activity_register.*
import java.util.*

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        supportActionBar?.hide()

        createAccount.setOnClickListener {
            performRegister()
        }

        alreadyAccount.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            Log.d("Main", "Try to show login activity")
        }

        selectPhoto.setOnClickListener {
            Log.d("Main", "Try to show photo selector")

            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, 0)
        }
    }

    var selectedPhotoUri: Uri? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0 && resultCode == RESULT_OK && data != null) {
            Log.d("Main", "Photo was selected")

            selectedPhotoUri = data.data

            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, selectedPhotoUri)

            selectPhotoImageViewRegister.setImageBitmap(bitmap)

            selectPhoto.alpha = 0f
        }
    }

    private fun performRegister() {
        val email = textEmailRegister.text.toString()
        val password = textPasswordRegister.text.toString()
        val passwordConfirm = textPasswordRegisterConfirm.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter an email and password", Toast.LENGTH_LONG).show()
            return
        }
        if (password != passwordConfirm) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_LONG).show()
            return
        }

        Log.d("Main", "Username is $email")
        Log.d("Main", "Password is $password")

        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("Main", "User created with ID: ${task.result?.user?.uid}")

                    uploadImageToFirebase()
                }
            }
            .addOnFailureListener {
                Log.d("Main", "Failed to create user: ${it.message}")
                Toast.makeText(this, "Invalid parameters", Toast.LENGTH_LONG).show()
            }
    }

    private fun uploadImageToFirebase() {

        if (selectedPhotoUri == null) {
            return
        }
        val filename = UUID.randomUUID().toString()
        val ref = FirebaseStorage.getInstance().getReference("/images/$filename")
        ref.putFile(selectedPhotoUri!!)
            .addOnSuccessListener {
                Log.d("Main", "Successfully uploaded image: ${it.metadata?.path}")

                ref.downloadUrl.addOnSuccessListener {
                    Log.d("Main", "File Location: $it")

                    saveUserToDatabase(it.toString())
                }
            }
            .addOnFailureListener {
                Log.d("Main", "Image upload failed")
            }
    }

    private fun saveUserToDatabase(profileImageUrl: String) {
        val uid = FirebaseAuth.getInstance().uid ?: ""
        val ref = FirebaseDatabase.getInstance().getReference("/users/$uid")
        val contactRef = FirebaseDatabase.getInstance().getReference("/users/$uid/contacts")

        val user = User(uid, textUsernameRegister.text.toString(), profileImageUrl, textEmailRegister.text.toString())

        FirebaseManager.user = user
        ref.setValue(user)
            .addOnSuccessListener {
                Log.d("Main", "Saved the user to Firebase Realtime Database")

                val intent = Intent(this, LatestMessagesActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK.or(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        contactRef.setValue(listOf<String>())
    }
}

@Parcelize
class User(val uid: String, var username: String, var profileImageUrl: String, var email: String) : Parcelable {
    constructor() : this("", "", "", "")
}