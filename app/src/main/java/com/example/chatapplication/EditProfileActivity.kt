package com.example.chatapplication

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.example.chatapplication.models.User
import com.example.chatapplication.objects.FirebaseManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_edit_profile.*
import kotlinx.android.synthetic.main.nav_header_navigation_drawer.*
import java.util.*

class EditProfileActivity : AppCompatActivity() {

    private val viewModel by lazy { ViewModelProvider(this)[EditProfileViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        supportActionBar?.elevation = 0f
        supportActionBar?.title = "Edit Profile"

        if (savedInstanceState == null) {
            val uid = FirebaseManager.user?.uid
            val ref = FirebaseDatabase.getInstance().getReference("/users/$uid")
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                }

                override fun onDataChange(p0: DataSnapshot) {
                    val user = p0.getValue(User::class.java)
                    Picasso.get().load(user?.profileImageUrl).into(userImageProfileEdit)
                    viewModel.profileImageUrl = user!!.profileImageUrl
                    usernameTextViewProfileEdit.setText(user.username)
                    emailTextViewProfileEdit.setText(user.email)
                }
            })
        } else {
            Picasso.get().load(viewModel.profileImageUrl).into(userImageProfileEdit)
            usernameTextViewProfileEdit.setText(viewModel.usernameText)
            emailTextViewProfileEdit.setText(viewModel.emailText)
        }

        changePhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, 0)
        }

        changePasswordEdit.setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        usernameTextViewProfileEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                viewModel.usernameText = usernameTextViewProfileEdit.text.toString()
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                invalidateOptionsMenu()
                viewModel.usernameText = usernameTextViewProfileEdit.text.toString()
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })

        emailTextViewProfileEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                viewModel.emailText = emailTextViewProfileEdit.text.toString()
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                invalidateOptionsMenu()
                viewModel.emailText = emailTextViewProfileEdit.text.toString()
            }
        })
    }

    private var newPhotoUri: Uri? = null
    private var bitmap: Bitmap? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0 && resultCode == Activity.RESULT_OK && data != null) {
            Log.d("PhotoChange", "Photo Change Begin")
            newPhotoUri = data.data

            bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, newPhotoUri!!))

            replaceImageInFirebase()
        }
    }

    private fun replaceImageInFirebase() {
        if (newPhotoUri == null) { return }
        val filename = UUID.randomUUID().toString()
        val ref = FirebaseStorage.getInstance().getReference("/images/$filename")
        ref.putFile(newPhotoUri!!).addOnSuccessListener {
            Log.d("PhotoChange", "Successfully uploaded image: ${it.metadata?.path}")

            ref.downloadUrl.addOnSuccessListener {
                Log.d("PhotoChange", "File location: $it")

                val uid = FirebaseManager.user?.uid
                val databaseRef = FirebaseDatabase.getInstance().getReference("/users/$uid").child("profileImageUrl")
                FirebaseManager.user?.profileImageUrl = it.toString()
                viewModel.profileImageUrl = it.toString()
                databaseRef.setValue(it.toString())
                userImageProfileEdit.setImageBitmap(bitmap)
                FirebaseManager.profileChanged = true
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val item = menu?.findItem(R.id.save_profile)
        val newName = usernameTextViewProfileEdit.text.toString()
        val newEmail = emailTextViewProfileEdit.text.toString()

        if  (newName != FirebaseManager.user?.username || newEmail != FirebaseManager.user?.email) {
            item?.isVisible = true
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.edit_profile_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.save_profile -> {
                val newUsername = usernameTextViewProfileEdit.text.toString()
                val newEmail = emailTextViewProfileEdit.text.toString()
                val uid = FirebaseManager.user!!.uid

                if (newEmail == FirebaseManager.user?.email) {
                    changeUsername(uid, newUsername)
                }
                if (newUsername == FirebaseManager.user?.username) {
                    changeEmail(uid, newEmail)
                }
                else if (newUsername != FirebaseManager.user?.username && newEmail != FirebaseManager.user?.email) {
                    changeUsername(uid, newUsername)
                    changeEmail(uid, newEmail)
                }
                FirebaseManager.profileChanged = true
                startActivity(Intent(this, ProfileActivity::class.java))
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun changeUsername(uid: String, newUsername: String) {
        val ref = FirebaseDatabase.getInstance().getReference("/users/$uid").child("username")
        FirebaseManager.user?.username = newUsername
        ref.setValue(newUsername)
    }

    private fun changeEmail(uid: String, newEmail: String) {
        val ref = FirebaseDatabase.getInstance().getReference("/users/$uid").child("email")
        FirebaseManager.user?.email = newEmail
        FirebaseAuth.getInstance().currentUser!!.updateEmail(newEmail)
        ref.setValue(newEmail)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.saveState(outState)
    }
}
