package com.example.photoreminder

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.photoreminder.data.api.RetrofitInstance
import com.example.photoreminder.databinding.FragmentImageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImageFragment : Fragment() {

    private val args: ImageFragmentArgs by navArgs()
    private var _binding: FragmentImageBinding? = null
    private val binding get() = _binding!!

    private var tmpFile: File? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /* back arrow */
        binding.materialToolbar2.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        loadImage()
    }

    private fun loadImage() {
        val thumbUri = Uri.fromFile(File(args.thumbPath))

        if (args.remoteId.isBlank() || !hasNetworkConnection(requireContext())) {
            Glide.with(this)
                .load(thumbUri)
                .into(binding.fullImage)
            binding.progressBar.visibility = View.GONE
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitInstance.api.downloadPhoto(args.remoteId)
                if (!response.isSuccessful || response.body() == null) {
                    throw IllegalStateException("HTTP ${response.code()}")
                }

                val cacheDir = File(requireContext().cacheDir, "photo_tmp").apply { mkdirs() }
                val dest = File.createTempFile("full_", ".jpg", cacheDir)
                response.body()!!.byteStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                tmpFile = dest

                withContext(Dispatchers.Main) {
                    Glide.with(this@ImageFragment)
                        .load(Uri.fromFile(dest))
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(binding.fullImage)
                    binding.progressBar.visibility = View.GONE
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Glide.with(this@ImageFragment)
                        .load(thumbUri)
                        .into(binding.fullImage)
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun hasNetworkConnection(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } else {

            cm.activeNetworkInfo?.isConnected == true
        }
    }

    override fun onDestroyView() {
        tmpFile?.delete()
        _binding = null
        super.onDestroyView()
    }
}
