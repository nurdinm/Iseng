package id.popo.iseng.ui.camera

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.util.Log
import android.util.Rational
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
import id.popo.iseng.databinding.ActivityCameraBinding
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


typealias LumaListener = (luma: Double, tes: Boolean) -> Unit

class CameraActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null


    private lateinit var cameraExecutor: ExecutorService
    private fun checkAccessibilityServicePermission() {
        var access = 0
        try {
            access = Settings.Secure.getInt(
                this.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: SettingNotFoundException) {
            e.printStackTrace()
        }
        if (access == 0) {
            val myIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            myIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(myIntent)
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
            requestAccessibility()
        } else {
            requestPermissions()
        }
        checkAccessibilityServicePermission()

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener {
            val d = windowManager.defaultDisplay
            val p = Point()
            d.getSize(p)
            val width = p.x
            val height = p.y

            val ratio = Rational(width, height)
            val pip_Builder = PictureInPictureParams.Builder()
            pip_Builder.setAspectRatio(ratio).build()
            enterPictureInPictureMode(pip_Builder.build())
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {

                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma, t ->
                        Log.d(TAG, "Average luminosity: $luma ")
                        if (t) {
                            Toast.makeText(this, "BLINK", Toast.LENGTH_SHORT).show()
                            val manager =
                                this.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
                            val event = AccessibilityEvent.obtain()
                            event.setEventType(AccessibilityEvent.TYPE_ANNOUNCEMENT)
                            manager.sendAccessibilityEvent(event)

                        }

                    })

                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
                requestAccessibility()
            }
        }

    private fun requestAccessibility() {
        if (!Settings.canDrawOverlays(this)) {
            val intent =
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            resultLauncher.launch(intent)
        }
    }

    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
        }
    }

    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        @OptIn(ExperimentalGetImage::class) override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            val mediaImage = image.image
            if (mediaImage != null) {
                val imaged = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                val realTimeOpts = FaceDetectorOptions.Builder()
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setPerformanceMode(PERFORMANCE_MODE_ACCURATE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                    .setMinFaceSize(0.1f)
                    .build()
                val detector = FaceDetection.getClient(realTimeOpts)
                detector.process(imaged)
                    .addOnSuccessListener { faces ->
                        for (face in faces) {
                            var blink = false
                            if (face.leftEyeOpenProbability != null) {
                                Log.e("DEBUG_MAIN_LEFT", face.leftEyeOpenProbability.toString())
                            }
                            if (face.rightEyeOpenProbability != null) {
                                Log.e("DEBUG_MAIN_RIGHT", face.rightEyeOpenProbability.toString())
                            }
                            if (face.leftEyeOpenProbability != null && face.rightEyeOpenProbability != null) {
                                blink = if (face.leftEyeOpenProbability!! < 0.01 || face.rightEyeOpenProbability!! < 0.01) {
                                    Log.e("DEBUG_MAIN", "Blinking")
                                    true
                                } else {
                                    false
                                }
                            }


                            listener(luma, blink)
                        }

                    }
                    .addOnFailureListener { e ->
                        Log.e("DEBUG_MAIN", e.toString())
                    }
                    .addOnCompleteListener {
                        mediaImage.close()
                        image.close()
                    }
            }


        }

    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}