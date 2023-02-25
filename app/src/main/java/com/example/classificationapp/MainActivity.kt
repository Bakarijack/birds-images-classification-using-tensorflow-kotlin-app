package com.example.classificationapp

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.classificationapp.databinding.ActivityMainBinding
import com.example.classificationapp.ml.BirdsModel
import org.tensorflow.lite.support.image.TensorImage
import java.io.IOException
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var imageView: ImageView
    private lateinit var button: Button
    private lateinit var tvOutPut: TextView
    private val GALLARY_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        imageView = binding.imageView
        button = binding.btnCaptureImage
        tvOutPut = binding.tvOutput
        val buttonLoad = binding.btnLoadImage

        button.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                takePicturePreview.launch(null)
            }else{
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }
        }

        buttonLoad.setOnClickListener{
            if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                intent.type = "image/*"
                val mimeTypes = arrayOf("image/jpeg","image/png","image/jpg")
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                onresult.launch(intent)
            }else{
                requestPermission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        //to render user to google search for the scientific name
        tvOutPut.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${tvOutPut.text}"))
            startActivity(intent)
        }

        //to download the image when long press
        imageView.setOnLongClickListener {
            requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return@setOnLongClickListener true
        }
    }

    //request camera permission
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()){granted ->
        if (granted){
            takePicturePreview.launch(null)
        }else{
            Toast.makeText(this, "Permission denied !! try again", Toast.LENGTH_SHORT).show()
        }
    }

    //launch camera and take picture
    private val takePicturePreview = registerForActivityResult(ActivityResultContracts.TakePicturePreview()){bitmap ->
        if (bitmap != null){
            imageView.setImageBitmap(bitmap)
            outputGenerator(bitmap)
        }
    }

    //to get image from gallary
    private val onresult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result ->
        Log.i("TAG", "This is the result ${result.data} ${result.resultCode} ")
        onResultReceived(GALLARY_REQUEST_CODE, result)
    }

    private fun onResultReceived(requestCode: Int, result: ActivityResult?){
        when (requestCode){
            GALLARY_REQUEST_CODE -> {
                if (result?.resultCode == Activity.RESULT_OK){
                    result.data?.data?.let { uri ->
                        Log.i("TAG","onResultReceived: $uri")
                        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                        imageView.setImageBitmap(bitmap)
                        outputGenerator(bitmap)
                    }
                }else{
                    Log.e("TAG", "OnActivityResult: error in selecting image")
                }
            }
        }
    }

    private fun outputGenerator(bitmap: Bitmap){
        //declaring tensor flow model variables
        val birdsModel = BirdsModel.newInstance(this)

       // converting bitmap into tensor flow image
        val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val tfimage = TensorImage.fromBitmap(newBitmap)

       // Process the image using trained model and sort it in descending order
        val outputs = birdsModel.process(tfimage)
            .probabilityAsCategoryList.apply{
                sortByDescending { it.score }
            }

        //getting result having high probability

        val highProbabilityOutput = outputs[0]
        tvOutPut.text = highProbabilityOutput.label
        Log.i("TAG","outputGenerator: $highProbabilityOutput")

    }

    //to download image to device
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){
        isGranted: Boolean ->
        if (isGranted){
            AlertDialog.Builder(this).setTitle("Download image ?")
                .setMessage("Do you want to download this image to your device ? ")
                .setPositiveButton("Yes"){_,_ ->
                    val drawable: BitmapDrawable = imageView.drawable as BitmapDrawable
                    val bitmap = drawable.bitmap
                    downloadImage(bitmap)
                }
                .setNegativeButton("No"){dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }else{
            Toast.makeText(this,"Please allow permission to download image",Toast.LENGTH_LONG).show()
        }
    }

    //fun that takes bitmap and store it in user's device
    private fun downloadImage(mBitmap: Bitmap): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Birds_Images"+System.currentTimeMillis()/1000)
            put(MediaStore.Images.Media.MIME_TYPE, "images/png")
        }
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        if (uri != null){
            contentResolver.insert(uri, contentValues)?.also {
                contentResolver.openOutputStream(it).use { outputStream ->
                    if (!mBitmap.compress(Bitmap.CompressFormat.PNG,100, outputStream)){
                        throw IOException("Couldn't save the bitmap")
                    }else{
                        Toast.makeText(applicationContext,"Image saved",Toast.LENGTH_LONG).show()
                    }
                }
                return it
            }
        }
        return  null
    }
}