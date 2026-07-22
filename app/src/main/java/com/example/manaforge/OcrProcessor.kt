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

    private val recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractLines(imagePath: String): Result<List<String>> =
        withContext(Dispatchers.IO) {

            try {
                val image = InputImage.fromFilePath(
                    context,
                    Uri.fromFile(File(imagePath))
                )

                val visionText = Tasks.await(recognizer.process(image))

                val lines = visionText.textBlocks.flatMap { block ->
                    block.lines.map { line ->
                        line.text.trim()
                    }
                }.filter {
                    it.isNotBlank()
                }

                Result.Success(lines)

            } catch (e: Exception) {
                Result.Error(
                    "Error al procesar la imagen: ${e.message}",
                    e
                )
            }
        }


    fun extractCardName(lines: List<String>): String? {

        if (lines.isEmpty()) {
            return null
        }

        return lines.first()
    }
}