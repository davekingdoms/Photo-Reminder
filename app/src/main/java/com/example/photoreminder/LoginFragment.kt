package com.example.photoreminder

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.example.photoreminder.databinding.FragmentLoginBinding
import com.example.photoreminder.ui.login.LoginViewModel
import androidx.fragment.app.viewModels



class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLoginBinding.inflate(inflater, container,false)
        val view = binding.root
        return view
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
                val username = binding.usernamEditText.text.toString()
                val password = binding.passwordEditText.text.toString()
                binding.loginButton.isEnabled = username.isNotEmpty() && password.isNotEmpty()
            }

            override fun afterTextChanged(s: Editable?) {}
        }
        binding.usernamEditText.addTextChangedListener(textWatcher)
        binding.passwordEditText.addTextChangedListener(textWatcher)

        binding.loginButton.setOnClickListener {
            val email = binding.usernamEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()
            loginViewModel.doLogin(email, password)
        }

        loginViewModel.loginResponse.observe(viewLifecycleOwner) { response ->
            response?.let {
                if (it.isSuccessful) {

                    val authResponse = it.body()
                    val token = authResponse?.token
                    val message = authResponse?.message

                    Toast.makeText(
                        requireContext(),
                        "Login success. Token: $token",
                        Toast.LENGTH_SHORT
                    ).show()

                    findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                    loginViewModel.clearLoginResponse()

                } else {
                    val errorBody = it.errorBody()?.string()
                    Toast.makeText(requireContext(), "Login error: $errorBody", Toast.LENGTH_LONG)
                        .show()
                }

                loginViewModel.errorMessage.observe(viewLifecycleOwner) { error ->
                    error?.let {
                        Toast.makeText(requireContext(), "Network error: $it", Toast.LENGTH_LONG).show()
                        loginViewModel.clearLoginResponse()
                    }
                }
            }

        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}