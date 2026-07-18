package com.example.manaforge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.example.manaforge.databinding.FragmentScanResultBinding
import java.io.File

class ScanResultFragment : Fragment() {

    private var _binding: FragmentScanResultBinding? = null
    private val binding get() = _binding!!
    private val args: ScanResultFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imageFile = File(args.imagePath)
        if (imageFile.exists()) {
            binding.imgCapture.load(imageFile) { crossfade(true) }
        }

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.btnRetake.setOnClickListener { findNavController().navigateUp() }

        binding.btnAddManually.setOnClickListener {
            findNavController().popBackStack(R.id.collectionFragment, false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
