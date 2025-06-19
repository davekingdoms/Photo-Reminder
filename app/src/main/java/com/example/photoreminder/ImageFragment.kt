package com.example.photoreminder

import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.photoreminder.data.api.RetrofitInstance
import com.example.photoreminder.databinding.FragmentImageBinding
import java.io.File

class ImageFragment : Fragment() {

    private val args: ImageFragmentArgs by navArgs()
    private var _binding: FragmentImageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val thumbUri = Uri.fromFile(File(args.thumbPath))
        val fullUrl =
            if (args.remoteId.isNotBlank())
                "${RetrofitInstance.BASE_URL}photos/${args.remoteId}"
            else null

        val iv = binding.fullImage
        val pb = binding.progressBar

        /* Se non c’è remoteId mostriamo subito la thumb */
        if (fullUrl == null) {
            Glide.with(this).load(thumbUri).into(iv)
            pb.visibility = View.GONE
            return
        }

        Glide.with(this)
            .load(fullUrl)
            .diskCacheStrategy(DiskCacheStrategy.NONE)   // stream, no file cache
            .error(Glide.with(this).load(thumbUri))      // fallback automatico
            .listener(object : RequestListener<android.graphics.drawable.Drawable> {

                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    model: Any,
                    target: Target<android.graphics.drawable.Drawable>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    pb.visibility = View.GONE
                    return false          // Glide continua la gestione
                }

                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<android.graphics.drawable.Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    pb.visibility = View.GONE
                    /* return false → Glide applica la drawable dell’.error() */
                    return false
                }
            })
            .into(iv)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
