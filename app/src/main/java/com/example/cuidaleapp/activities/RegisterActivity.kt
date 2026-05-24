package com.example.cuidaleapp.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme

import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.cuidaleapp.ui.screens.RegisterScreen
import com.example.cuidaleapp.viewmodel.RegisterViewModel
import com.example.cuidaleapp.viewmodel.ThemeViewModel
import com.example.cuidaleapp.viewmodel.RegisterStep


class RegisterActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels()
    private val registerViewModel: RegisterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val emailToVerify = intent.getStringExtra("EMAIL_TO_VERIFY")
        val startAtVerification = intent.getBooleanExtra("START_AT_VERIFICATION", false)

        if (startAtVerification && emailToVerify != null) {
            registerViewModel.prepareForVerification(emailToVerify)
        } else {
            val role = intent.getStringExtra("ROLE") ?: "representante"
            registerViewModel.setRole(role)
        }

        setContent {
            val isDark by themeViewModel.isDarkMode.collectAsState()
            val currentStep by registerViewModel.currentStep.collectAsState()
            val userData by registerViewModel.userData.collectAsState()

            LaunchedEffect(currentStep) {
                if (currentStep == RegisterStep.Verification) {
                    val intent = Intent(this@RegisterActivity, VerificationActivity::class.java).apply {
                        putExtra("EMAIL_TO_VERIFY", userData.correo)
                    }
                    startActivity(intent)
                    finish()
                }
            }

            CuidaleAppTheme(darkTheme = isDark) {
                RegisterScreen(
                    viewModel = registerViewModel,
                    onBackToLogin = {
                        finish()
                    }
                )
            }
        }
    }
}



@Composable
fun Greeting2(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview2() {
    CuidaleAppTheme {
        Greeting2("Android")
    }
}