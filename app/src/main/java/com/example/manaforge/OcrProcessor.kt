package com.example.manaforge

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractText(imagePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromFilePath(context, Uri.fromFile(File(imagePath)))
            val visionText = Tasks.await(recognizer.process(inputImage))
            Result.Success(visionText.text)
        } catch (e: Exception) {
            Result.Error("OCR failed: ${e.message}", e)
        }
    }
}
