package com.example.predictsafe

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val loggedInEmail = prefs.getString("logged_in_email", null)
        val loggedInName = prefs.getString("logged_in_name", null)
        if (!loggedInEmail.isNullOrEmpty() && !loggedInName.isNullOrEmpty()) {
            val intent = Intent(this, MapsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            return
        }
        setContentView(R.layout.activity_login)

        val emailEdit = findViewById<EditText>(R.id.loginEmail)
        val passwordEdit = findViewById<EditText>(R.id.loginPassword)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val errorText = findViewById<TextView>(R.id.loginError)
        val registerLink = findViewById<TextView>(R.id.loginRegisterLink)

        loginButton.setOnClickListener {
            val email = emailEdit.text.toString().trim()
            val password = passwordEdit.text.toString()
            if (email.isEmpty() || password.isEmpty()) {
                errorText.text = "Please enter email and password."
                errorText.visibility = TextView.VISIBLE
                return@setOnClickListener
            }
            val user = getUserByEmail(email)
            if (user == null || user.password != password) {
                errorText.text = "Invalid email or password."
                errorText.visibility = TextView.VISIBLE
            } else {
                // Save logged-in user info
                val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("logged_in_email", user.email).apply()
                prefs.edit().putString("logged_in_name", user.name).apply()
                // Go to main
                val intent = Intent(this, MapsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }
        registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun getUserByEmail(email: String): User? {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("users_json", null) ?: return null
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("email") == email) {
                return User(obj.getString("name"), obj.getString("email"), obj.getString("password"))
            }
        }
        return null
    }

    data class User(val name: String, val email: String, val password: String)
} 