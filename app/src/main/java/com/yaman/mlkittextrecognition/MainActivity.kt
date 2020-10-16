package com.yaman.mlkittextrecognition

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.util.SparseArray
import android.view.SurfaceView
import android.view.View
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.huawei.hms.mlsdk.common.LensEngine
import com.huawei.hms.mlsdk.common.MLAnalyzer
import com.huawei.hms.mlsdk.common.MLApplication
import com.huawei.hms.mlsdk.text.MLText
import com.huawei.hms.mlsdk.text.MLTextAnalyzer
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private val TAG: String = MainActivity::class.java.simpleName
    val REQUEST_TAKE_PHOTO = 2
    val REQUEST_CAMERA_STREAM = 3
    lateinit var currentPhotoPath: String
    private val PERMISSION_CODE = 1
    private val GET_IMAGE_REQUEST_CODE = 22

    var lensEngine: LensEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestCameraPermission()

        /**
         * Use Cloud Function
         */
        imgTakeFromCameraCard.setOnClickListener {
            clearText()
            surfaceGone()
            MLApplication.getInstance().apiKey = getString(R.string.apikey)
            dispatchTakePictureIntent()
        }

        /**
         * Use onDevice Function
         */
        imgGetFromGalleryCard.setOnClickListener {
            //   clearText()
            //   surfaceGone()
            getImage()
        }

        /**
         * Use Text Recognition From Camera Stream
         */
        cameraStreamCard.setOnClickListener {
            // clearText()
            surfaceVisible()
            cameraStream()
        }


    }

    private fun getImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, GET_IMAGE_REQUEST_CODE)
    }

    private fun requestCameraPermission() {
        val permissions = arrayOf<String>(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CALL_PHONE
        )
        if (!ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.CAMERA
            )
            && !ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        ) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_CODE)
            return
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_CODE) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            return
        }
        if (grantResults.size != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            return
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, @Nullable data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == Activity.RESULT_OK) {
            setPic()
        }
        if (requestCode == GET_IMAGE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {

            val imageUri: Uri = data.data!!
            try {
                val selectedBitmap =
                    MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                iv_bitmap!!.setImageBitmap(selectedBitmap)
                performTextRecognitionOnDevice(selectedBitmap)
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }

    }


    private fun performTextRecognitionOnCloud(bitmap: Bitmap) {
        val result = RecognitionUtil.asyncAnalyzeTextCloud(bitmap, etResult)
    }

    private fun performTextRecognitionOnDevice(bitmap: Bitmap) {
        val result = RecognitionUtil.asyncAnalyzeTextLocal(bitmap, etResult)
    }


    private fun cameraStream() {
        val analyzer = MLTextAnalyzer.Factory(this).create()
        analyzer.setTransactor(OcrDetectorProcessor(etResult))
        lensEngine = LensEngine.Creator(applicationContext, analyzer)
            .setLensType(LensEngine.BACK_LENS)
            .applyDisplayDimension(1440, 1080)
            .applyFps(30.0f)
            .enableAutomaticFocus(true).create()
        val mSurfaceView: SurfaceView = findViewById(R.id.surface)
        try {
            lensEngine!!.run(mSurfaceView.holder)
        } catch (e: IOException) {
            //handle Exception
        }
    }

    private fun surfaceVisible() {
        imageArea.visibility = View.GONE
        surface.visibility = View.VISIBLE
    }

    private fun surfaceGone() {
        imageArea.visibility = View.VISIBLE
        surface.visibility = View.GONE
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.yaman.mlkittextrecognition.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File = this.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun setPic() {
        // Get the dimensions of the View
        val targetW: Int = iv_bitmap.width
        val targetH: Int = iv_bitmap.height

        val bmOptions = BitmapFactory.Options().apply {
            // Get the dimensions of the bitmap
            inJustDecodeBounds = true

            val photoW: Int = outWidth
            val photoH: Int = outHeight

            // Determine how much to scale down the image
            val scaleFactor: Int = Math.max(1, Math.min(photoW / targetW, photoH / targetH))

            // Decode the image file into a Bitmap sized to fill the View
            inJustDecodeBounds = false
            inSampleSize = scaleFactor
        }

        BitmapFactory.decodeFile(currentPhotoPath, bmOptions)?.also { bitmap ->
            iv_bitmap.setImageBitmap(bitmap)
            performTextRecognitionOnCloud(bitmap)
        }
    }

    private fun clearText() {
        etResult.text?.clear()
    }

    private fun releaseAnalyzerAndLensEngine() {
        if (lensEngine != null) {
            lensEngine!!.release()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            RecognitionUtil.releaseAnalyzer()
            releaseAnalyzerAndLensEngine()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    class OcrDetectorProcessor(
        etResult: AppCompatEditText
    ) :
        MLAnalyzer.MLTransactor<MLText.Block?> {

        private var etResult: AppCompatEditText? = null

        init {
            this.etResult = etResult
        }

        override fun transactResult(results: MLAnalyzer.Result<MLText.Block?>) {
            val items: SparseArray<MLText.Block?>? = results.analyseList
            var result = ""

            for (i in 0 until items!!.size()) {
                result = " \n $result ${items[i]?.stringValue}"
            }

            etResult?.text?.clear()
            etResult?.text = Editable.Factory.getInstance().newEditable(result)
        }

        override fun destroy() {
            // Callback method used to release resources when the detection ends.
        }

    }


}