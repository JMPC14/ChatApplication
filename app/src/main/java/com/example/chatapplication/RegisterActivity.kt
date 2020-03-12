package com.example.chatapplication

import android.content.Intent
import android.graphics.ImageDecoder
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

        createAccountButtonRegister.setOnClickListener {
            performRegister()
        }

        alreadyAccountRegister.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        selectPhotoRegister.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, 0)
        }
    }

    private var selectedPhotoUri: Uri? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 0 && resultCode == RESULT_OK && data != null) {
            selectedPhotoUri = data.data
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, selectedPhotoUri!!))
            selectPhotoImageViewRegister.setImageBitmap(bitmap)
            selectPhotoRegister.alpha = 0f
        }
    }

    private fun performRegister() {
        val username = textUsernameRegister.text.toString()
        val email = textEmailRegister.text.toString()
        val password = textPasswordRegister.text.toString()
        val passwordConfirm = textPasswordRegisterConfirm.text.toString()

        if (email.isEmpty() || password.isEmpty() || username.isEmpty()) {
            Toast.makeText(this, "Please enter a username, email, and password", Toast.LENGTH_LONG).show()
            return
        }

        if (password != passwordConfirm) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_LONG).show()
            textPasswordRegister.text.clear()
            textPasswordRegisterConfirm.text.clear()
            return
        }

        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("Main", "User created with ID: ${task.result?.user?.uid}")

                    uploadImageToFirebase()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Invalid parameters: ${it.message}", Toast.LENGTH_LONG).show()
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