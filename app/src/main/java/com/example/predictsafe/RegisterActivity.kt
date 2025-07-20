package com.example.predictsafe

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val nameEdit = findViewById<EditText>(R.id.registerName)
        val emailEdit = findViewById<EditText>(R.id.registerEmail)
        val passwordEdit = findViewById<EditText>(R.id.registerPassword)
        val confirmEdit = findViewById<EditText>(R.id.registerConfirmPassword)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val errorText = findViewById<TextView>(R.id.registerError)
        val loginLink = findViewById<TextView>(R.id.registerLoginLink)

        registerButton.setOnClickListener {
            val name = nameEdit.text.toString().trim()
            val email = emailEdit.text.toString().trim()
            val password = passwordEdit.text.toString()
            val confirm = confirmEdit.text.toString()
            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
                errorText.text = "Please fill all fields."
                errorText.visibility = TextView.VISIBLE
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                errorText.text = "Invalid email format."
                errorText.visibility = TextView.VISIBLE
                return@setOnClickListener
            }
            if (password.length < 6) {
                errorText.text = "Password must be at least 6 characters."
                errorText.visibility = TextView.VISIBLE
                return@setOnClickListener
            }
            if (password != confirm) {
                errorText.text = "Passwords do not match."
                errorText.visibility = TextView.VISIBLE
                return@setOnClickListener
            }
            if (userExists(email)) {
                errorText.text = "Email already registered."
                errorText.visibility = TextView.VISIBLE
                return@setOnClickListener
            }
            saveUser(name, email, password)
            // Auto-login after registration
            val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("logged_in_email", email).apply()
            prefs.edit().putString("logged_in_name", name).apply()
            val intent = Intent(this, MapsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        loginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun userExists(email: String): Boolean {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("users_json", null) ?: return false
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("email") == email) return true
        }
        return false
    }

    private fun saveUser(name: String, email: String, password: String) {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("users_json", null)
        val arr = if (json != null) JSONArray(json) else JSONArray()
        val obj = JSONObject()
        obj.put("name", name)
        obj.put("email", email)
        obj.put("password", password)
        arr.put(obj)
        prefs.edit().putString("users_json", arr.toString()).apply()
    }
} 