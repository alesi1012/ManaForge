package com.example.manaforge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.manaforge.databinding.FragmentCameraBinding
import com.google.android.material.snackbar.Snackbar
import java.io.File

private const val TAG = "ManaForge"

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else showPermissionDenied()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnCapture.setOnClickListener { captureImage() }
        checkAndStartCamera()
    }

    private fun checkAndStartCamera() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> startCamera()

            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Camera required")
                    .setMessage("The camera is needed to scan Magic: The Gathering cards.")
                    .setPositiveButton("Allow") { _, _ ->
                        requestPermission.launch(Manifest.permission.CAMERA)
                    }
                    .setNegativeButton("Cancel") { _, _ -> findNavController().navigateUp() }
                    .show()
            }

            else -> requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            val provider = future.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "CameraX bind failed", e)
                Snackbar.make(binding.root, "Camera error: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun captureImage() {
        val capture = imageCapture ?: return
        val file = File(requireContext().cacheDir, "scan_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        binding.btnCapture.isEnabled = false

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val action = CameraFragmentDirections.actionCameraToScanResult(file.absolutePath)
                    findNavController().navigate(action)
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Capture failed: ${exc.message}", exc)
                    binding.btnCapture.isEnabled = true
                    Snackbar.make(binding.root, "Capture failed. Try again.", Snackbar.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun showPermissionDenied() {
        Snackbar.make(binding.root, "Camera permission required to scan cards", Snackbar.LENGTH_LONG)
            .setAction("OK") { findNavController().navigateUp() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class CardOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val overlayPaint = Paint().apply { color = 0xBB000000.toInt() }

    private val cornerPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val cardRect = RectF()

    override fun onDraw(canvas: Canvas) {
        val cardW = width * 0.72f
        val cardH = cardW / 0.714f
        val left = (width - cardW) / 2f
        val top = (height - cardH) / 2.3f
        cardRect.set(left, top, left + cardW, top + cardH)

        val l = cardRect.left
        val t = cardRect.top
        val r = cardRect.right
        val b = cardRect.bottom

        // Dark overlay (4 rects leaving the card area transparent)
        canvas.drawRect(0f, 0f, width.toFloat(), t, overlayPaint)
        canvas.drawRect(0f, b, width.toFloat(), height.toFloat(), overlayPaint)
        canvas.drawRect(0f, t, l, b, overlayPaint)
        canvas.drawRect(r, t, width.toFloat(), b, overlayPaint)

        // Corner L-shapes (white brackets at each corner)
        val arm = 60f
        // Top-left
        canvas.drawLine(l, t, l + arm, t, cornerPaint)
        canvas.drawLine(l, t, l, t + arm, cornerPaint)
        // Top-right
        canvas.drawLine(r - arm, t, r, t, cornerPaint)
        canvas.drawLine(r, t, r, t + arm, cornerPaint)
        // Bottom-left
        canvas.drawLine(l, b, l + arm, b, cornerPaint)
        canvas.drawLine(l, b - arm, l, b, cornerPaint)
        // Bottom-right
        canvas.drawLine(r - arm, b, r, b, cornerPaint)
        canvas.drawLine(r, b - arm, r, b, cornerPaint)
    }
}
