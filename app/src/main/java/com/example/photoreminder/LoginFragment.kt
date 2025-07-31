package com.example.photoreminder

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.photoreminder.data.datastore.DataStoreManager
import com.example.photoreminder.data.sync.MarkerSyncWorker
import com.example.photoreminder.databinding.FragmentLoginBinding
import com.example.photoreminder.ui.login.LoginViewModel
import kotlinx.coroutines.launch
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import com.example.photoreminder.data.sync.PhotoThumbWorker
import java.util.concurrent.TimeUnit


@Suppress("LABEL_NAME_CLASH")
class LoginFragment : Fragment() {


    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.registerTextView.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        binding.loginButton.isEnabled = false
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val username = binding.usernameEditText.text.toString()
                val password = binding.passwordEditText.text.toString()
                binding.loginButton.isEnabled = username.isNotEmpty() && password.isNotEmpty()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.usernameEditText.addTextChangedListener(textWatcher)
        binding.passwordEditText.addTextChangedListener(textWatcher)

        binding.loginButton.setOnClickListener {
            val username = binding.usernameEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()
            loginViewModel.doLogin(username, password)
        }

        loginViewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), "Network error: $it", Toast.LENGTH_LONG).show()
                loginViewModel.clearLoginResponse()
            }
        }

        loginViewModel.loginResponse.observe(viewLifecycleOwner) { response ->
            response?.let {
                if (it.isSuccessful) {
                    val authResponse = it.body()
                    val token = authResponse?.token
                    val username = binding.usernameEditText.text.toString().trim()

                    viewLifecycleOwner.lifecycleScope.launch {
                        if (token != null) {

                            DataStoreManager.saveToken(requireContext(), token)
                            DataStoreManager.saveUsername(requireContext(), username)

                            requireContext()
                                .getSharedPreferences("marker_sync_prefs", Context.MODE_PRIVATE)
                                .edit {
                                    putLong("last_sync_time_$username", 0L)
                                }

                            val markerReq = OneTimeWorkRequestBuilder<MarkerSyncWorker>()
                                .setInputData(workDataOf("isManual" to true))
                                .setConstraints(
                                    Constraints.Builder()
                                        .setRequiredNetworkType(NetworkType.CONNECTED)
                                        .build()
                                )
                                .setBackoffCriteria(
                                    BackoffPolicy.EXPONENTIAL,
                                    30, TimeUnit.SECONDS
                                )
                                .build()

                            val photoReq = OneTimeWorkRequestBuilder<PhotoThumbWorker>()
                                .setConstraints(
                                    Constraints.Builder()
                                        .setRequiredNetworkType(NetworkType.CONNECTED)
                                        .build()
                                )
                                .setBackoffCriteria(
                                    BackoffPolicy.EXPONENTIAL,
                                    30, TimeUnit.SECONDS
                                )
                                .build()

                            val wm = WorkManager.getInstance(requireContext())
                            wm.beginUniqueWork(
                                MarkerSyncWorker.QUEUE_MANUAL,
                                ExistingWorkPolicy.REPLACE,
                                markerReq
                            )
                                .then(photoReq)
                                .enqueue()

                            wm.getWorkInfosForUniqueWorkLiveData(MarkerSyncWorker.QUEUE_MANUAL)
                                .observe(viewLifecycleOwner) { infos ->
                                    val info = infos.firstOrNull() ?: return@observe
                                    if (info.state.isFinished) {
                                        val msg = info.outputData.getString("message")
                                            ?: info.outputData.getString("errorMessage")
                                        if (!msg.isNullOrBlank()) {
                                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                                        }
                                        WorkManager.getInstance(requireContext()).pruneWork()
                                    }
                                }

                            findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                            loginViewModel.clearLoginResponse()
                        }
                    }
                } else {
                    val errorBody = it.errorBody()?.string()
                    Toast.makeText(requireContext(), "Login error: $errorBody", Toast.LENGTH_LONG).show()
                }
            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
