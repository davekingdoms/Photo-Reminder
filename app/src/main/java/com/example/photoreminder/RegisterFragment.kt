package com.example.photoreminder

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.photoreminder.data.datastore.DataStoreManager
import com.example.photoreminder.databinding.FragmentRegisterBinding
import com.example.photoreminder.ui.register.RegisterViewModel
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!


    private val registerViewModel: RegisterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        binding.registerButton.setOnClickListener {
            val email = binding.nameRegisterEditText.text.toString().trim()
            val password = binding.passwordRegisterEditText.text.toString().trim()
            val repeatPassword = binding.repeatPasswordRegisterEditText.text.toString().trim()

            if (password != repeatPassword) {
                Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show()
                binding.repeatPasswordRegisterEditText.error = "Passwords do not match"
                binding.repeatPasswordRegisterEditText.requestFocus()
                return@setOnClickListener
            }

            registerViewModel.doRegister(email, password)
        }

        registerViewModel.registerResponse.observe(viewLifecycleOwner) { response ->
            response?.let {
                if (it.isSuccessful) {
                    val authResponse = it.body()
                    val msg = authResponse?.message
                    val token = authResponse?.token

                    viewLifecycleOwner.lifecycleScope.launch {
                        if (token != null) {
                            DataStoreManager.saveToken(requireContext(), token)
                            Log.d("TOKEN", "Token: $token")
                            findNavController().navigate(R.id.action_registerFragment_to_homeFragment)
                            Toast.makeText(requireContext(), "Registered! $msg", Toast.LENGTH_SHORT).show()
                            registerViewModel.clearRegisterResponse()
                        }
                        else {
                            Log.d("TOKEN", "null")
                        }
                    }



                } else {
                    val errorBody = it.errorBody()?.string()
                    Toast.makeText(requireContext(), "Register error: $errorBody", Toast.LENGTH_LONG).show()
                }
            }
        }

        registerViewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), "Network error: $it", Toast.LENGTH_LONG).show()
                registerViewModel.clearRegisterResponse()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
