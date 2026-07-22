package com.example.manaforge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.example.manaforge.databinding.FragmentScanResultBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ScanResultFragment : Fragment() {

    private var _binding: FragmentScanResultBinding? = null
    private val binding get() = _binding!!

    private val args: ScanResultFragmentArgs by navArgs()

    @Inject
    lateinit var ocrProcessor: OcrProcessor

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val imageFile = File(args.imagePath)

        if (imageFile.exists()) {
            binding.imgCapture.load(imageFile) {
                crossfade(true)
            }
        }

        // Ejecutamos el OCR
        processImage()

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnRetake.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnAddManually.setOnClickListener {
            findNavController().popBackStack(R.id.collectionFragment, false)
        }
    }

    private fun processImage() {

        binding.progressOcr.visibility = View.VISIBLE
        binding.tvResultTitle.text = "Analizando carta..."
        binding.tvResultSubtitle.text =
            "Estamos intentando reconocer la carta."

        lifecycleScope.launch {

            when (val result = ocrProcessor.extractLines(args.imagePath)) {

                is Result.Success -> {

                    val cardName =
                        ocrProcessor.extractCardName(result.data)

                    binding.progressOcr.visibility = View.GONE

                    if (cardName != null) {

                        binding.tvResultTitle.text = cardName

                        binding.tvResultSubtitle.text =
                            "Carta reconocida correctamente."

                    } else {

                        binding.tvResultTitle.text =
                            "Carta no reconocida"

                        binding.tvResultSubtitle.text =
                            "Intenta hacer la foto de nuevo."
                    }
                }

                is Result.Error -> {

                    binding.progressOcr.visibility = View.GONE

                    binding.tvResultTitle.text =
                        "Error"

                    binding.tvResultSubtitle.text =
                        result.message
                }

                Result.Loading -> {

                    binding.progressOcr.visibility = View.VISIBLE

                    binding.tvResultTitle.text =
                        "Analizando carta..."

                    binding.tvResultSubtitle.text =
                        "Estamos intentando reconocer la carta."
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}