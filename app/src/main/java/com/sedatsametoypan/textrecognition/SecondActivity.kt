package com.sedatsametoypan.textrecognition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptions
import com.sedatsametoypan.textrecognition.databinding.ActivitySecondBinding
import java.io.IOException
import java.util.*

class SecondActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySecondBinding
    private lateinit var activityResultLauncher : ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher : ActivityResultLauncher<String>
    private lateinit var db : FirebaseFirestore
    private lateinit var storage : FirebaseStorage
    private lateinit var auth : FirebaseAuth
    var selectedPicture : Uri? = null
    private lateinit var email : String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        db = Firebase.firestore
        storage = Firebase.storage
        if(auth.currentUser != null) {
            email = auth.currentUser!!.email.toString()
        }

        binding = ActivitySecondBinding.inflate(layoutInflater)
        setContentView(binding.root)
        try {
            this.supportActionBar!!.hide()
        } catch (e: NullPointerException) {
        }

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_second)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        navView.getMenu().findItem(R.id.navigation_notifications).setOnMenuItemClickListener { menuItem ->
            auth.signOut()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            true
        }
        registerLauncher()
    }
    fun selectImage(view: View) {
        if(ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Snackbar.make(view,"Permission Needed For Galery", Snackbar.LENGTH_INDEFINITE).setAction("Give Permission"){
                    // request permission
                    permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }.show()
            }else {
                // request permission
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        else {
            val intentToGalery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            // startActivity for result
            activityResultLauncher.launch(intentToGalery)
        }
    }

    private fun registerLauncher() {
        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if(result.resultCode == AppCompatActivity.RESULT_OK) {
                val intentFromResult = result.data
                if(intentFromResult != null) {
                    selectedPicture = intentFromResult.data
                    selectedPicture?.let {
                        var imageView : ImageView = findViewById(R.id.imageView)
                        imageView.setImageURI(it)
                        var textView2 : TextView = findViewById(R.id.textView2)
                        textView2.setText("")
                        readImage()
                    }
                }
            }
        }
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            if(result) {
                // permission granted
                val intentToGalery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                activityResultLauncher.launch(intentToGalery)
            }else{
                // permission denied
                Toast.makeText(this, "Permission Neeeded For Galery", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun readImage() {
        val image: InputImage
        val reference = storage.reference
        val newUUID = UUID.randomUUID()
        val imageName = "$newUUID.jpg"
        val imageReference = reference.child("images").child(imageName)
        try {
            image = InputImage.fromFilePath(this, selectedPicture)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // Task completed successfully
                    val resultText = visionText.text
                    val textView : TextView = findViewById(R.id.textView)
                    textView.setText(resultText)
                    if(selectedPicture != null) {
                        imageReference.putFile(selectedPicture!!).addOnSuccessListener {
                            // download url -> firestore
                            val uploadPictureReference = storage.reference.child("images").child(imageName)
                            uploadPictureReference.downloadUrl.addOnSuccessListener {
                                var downloadUrl = it.toString()
                                writeDataToDatabase(downloadUrl,resultText,newUUID.toString(),
                                    Timestamp.now())
                            }
                        }.addOnFailureListener {
                            Toast.makeText(this,it.localizedMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    fun writeDataToDatabase(downloadUrl : String,text : String,id : String, date : Timestamp) {
        var textMap = hashMapOf<String,Any>()
        textMap.put("id",id)
        textMap.put("text",text)
        textMap.put("downloadUrl",downloadUrl)
        textMap.put("date",date)
        textMap.put("userEmail",email)
        db.collection("Texts").add(textMap)
    }
    /*
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val menuInflater = menuInflater
        menuInflater.inflate(R.menu.text_recog_menu,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == R.id.signout) {
            auth.signOut()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
     */
}

