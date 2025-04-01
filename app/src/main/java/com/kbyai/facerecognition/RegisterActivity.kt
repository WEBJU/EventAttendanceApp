package com.kbyai.facerecognition

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class RegisterActivity : AppCompatActivity() {

    private lateinit var editFullName: EditText
    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var editConfirmPassword: EditText
    private lateinit var buttonRegister: Button
    private lateinit var textLogin: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        editFullName = findViewById(R.id.editFullName)
        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)
        editConfirmPassword = findViewById(R.id.editConfirmPassword)
        buttonRegister = findViewById(R.id.buttonRegister)
        textLogin = findViewById(R.id.textLogin)
        progressBar = findViewById(R.id.progressBar)

        buttonRegister.setOnClickListener { registerUser() }
        textLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerUser() {
        val fullName = editFullName.text.toString().trim()
        val email = editEmail.text.toString().trim()
        val password = editPassword.text.toString().trim()
        val confirmPassword = editConfirmPassword.text.toString().trim()

        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        val client = OkHttpClient()
        val jsonObject = JSONObject()
        jsonObject.put("name", fullName)
        jsonObject.put("email", email)
        jsonObject.put("password", password)

        val requestBody = RequestBody.create(
            MediaType.parse("application/json; charset=utf-8"),
            jsonObject.toString()
        )

        val request = Request.Builder()
            .url("https://your-api.com/api/register")  // Change to your API URL
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@RegisterActivity, "Network Error", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                }
                val responseBody = response.body()?.string()
                if (response.isSuccessful && responseBody != null) {
                    runOnUiThread {
                        Toast.makeText(this@RegisterActivity, "Registration Successful!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                        finish()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@RegisterActivity, "Registration Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}