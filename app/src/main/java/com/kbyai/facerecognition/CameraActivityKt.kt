package com.kbyai.facerecognition

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kbyai.facerecognition.SettingsActivity.Companion.getIdentifyThreshold
import com.kbyai.facerecognition.SettingsActivity.Companion.getLivenessLevel
import com.kbyai.facerecognition.SettingsActivity.Companion.getLivenessThreshold
import com.kbyai.facesdk.FaceDetectionParam
import com.kbyai.facesdk.FaceSDK
import io.fotoapparat.Fotoapparat
import io.fotoapparat.parameter.Resolution
import io.fotoapparat.preview.Frame
import io.fotoapparat.preview.FrameProcessor
import io.fotoapparat.selector.front
import io.fotoapparat.selector.back
import io.fotoapparat.view.CameraView

class CameraActivityKt : AppCompatActivity() {

    private val TAG = CameraActivityKt::class.java.simpleName
    private val PREVIEW_WIDTH = 720
    private val PREVIEW_HEIGHT = 1280

    private lateinit var cameraView: CameraView
    private lateinit var faceView: FaceView
    private lateinit var fotoapparat: Fotoapparat
    private lateinit var context: Context

    private var recognized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_kt)

        context = this
        cameraView = findViewById(R.id.preview)
        faceView = findViewById(R.id.faceView)

        if (SettingsActivity.getCameraLens(context) == CameraSelector.LENS_FACING_BACK) {
            fotoapparat = Fotoapparat.with(this)
                .into(cameraView)
                .lensPosition(back())
                .frameProcessor(FaceFrameProcessor())
                .previewResolution { Resolution(PREVIEW_HEIGHT, PREVIEW_WIDTH) }
                .build()
        } else {
            fotoapparat = Fotoapparat.with(this)
                .into(cameraView)
                .lensPosition(front())
                .frameProcessor(FaceFrameProcessor())
                .previewResolution { Resolution(PREVIEW_HEIGHT, PREVIEW_WIDTH) }
                .build()
        }

        // Request camera permissions if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        } else {
            fotoapparat.start()
        }
    }

    override fun onResume() {
        super.onResume()
        recognized = false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fotoapparat.start()
        }
    }

    override fun onPause() {
        super.onPause()
        fotoapparat.stop()
        faceView.setFaceBoxes(null)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                fotoapparat.start()
            }
        }
    }

    inner class FaceFrameProcessor : FrameProcessor {

        override fun process(frame: Frame) {
            if (recognized) {
                return
            }

            // Check for null frame or size
            if (frame.image == null || frame.size == null) {
                Log.e(TAG, "Frame or Frame size is null!")
                return
            }

            var cameraMode = 7
            if (SettingsActivity.getCameraLens(context) == CameraSelector.LENS_FACING_BACK) {
                cameraMode = 6
            }

            // Convert YUV to Bitmap
            val bitmap = FaceSDK.yuv2Bitmap(frame.image, frame.size.width, frame.size.height, cameraMode)

            // Ensure bitmap is valid
            if (bitmap == null || bitmap.width == 0 || bitmap.height == 0) {
                Log.e(TAG, "Bitmap is invalid!")
                return
            }

            val faceDetectionParam = FaceDetectionParam()
            faceDetectionParam.check_liveness = true
            faceDetectionParam.check_liveness_level = getLivenessLevel(context)
            val faceBoxes = FaceSDK.faceDetection(bitmap, faceDetectionParam)

            // Update faceView on UI thread
            runOnUiThread {
                faceView.setFrameSize(Size(bitmap.width, bitmap.height))
                faceView.setFaceBoxes(faceBoxes)
            }

            if (faceBoxes.isNotEmpty()) {
                val faceBox = faceBoxes[0]
                if (faceBox.liveness > SettingsActivity.getLivenessThreshold(context)) {
                    val templates = FaceSDK.templateExtraction(bitmap, faceBox)

                    var maxSimilarity = 0f
                    var maxSimilarityPerson: Person? = null

                    // Find the most similar person from the database
                    for (person in DBManager.personList) {
                        val similarity = FaceSDK.similarityCalculation(templates, person.templates)
                        if (similarity > maxSimilarity) {
                            maxSimilarity = similarity
                            maxSimilarityPerson = person
                        }
                    }

                    // Check if the similarity is above the threshold
                    if (maxSimilarity > SettingsActivity.getIdentifyThreshold(context)) {
                        recognized = true
                        val identifiedPerson = maxSimilarityPerson
                        val identifiedSimilarity = maxSimilarity

                        // Intent to show the result
                        runOnUiThread {
                            val faceImage = Utils.cropFace(bitmap, faceBox)
                            val intent = Intent(context, ResultActivity::class.java)
                            intent.putExtra("identified_face", faceImage)
                            intent.putExtra("enrolled_face", identifiedPerson!!.face)
                            intent.putExtra("identified_name", identifiedPerson!!.name)
                            intent.putExtra("similarity", identifiedSimilarity)
                            intent.putExtra("liveness", faceBox.liveness)
                            intent.putExtra("yaw", faceBox.yaw)
                            intent.putExtra("roll", faceBox.roll)
                            intent.putExtra("pitch", faceBox.pitch)
                            startActivity(intent)
                        }
                    }
                }
            }
        }
    }
}
