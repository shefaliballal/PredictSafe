package com.example.predictsafe

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val nameEdit = findViewById<EditText>(R.id.profileName)
        val emailView = findViewById<TextView>(R.id.profileEmail)
        val saveButton = findViewById<Button>(R.id.profileSaveButton)

        nameEdit.setText(prefs.getString("logged_in_name", ""))
        emailView.text = prefs.getString("logged_in_email", "")

        saveButton.setOnClickListener {
            val newName = nameEdit.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Save to logged-in user and update user list
            val email = prefs.getString("logged_in_email", "") ?: ""
            val usersJson = prefs.getString("users_json", null)
            if (usersJson != null) {
                val arr = org.json.JSONArray(usersJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.getString("email") == email) {
                        obj.put("name", newName)
                        break
                    }
                }
                prefs.edit().putString("users_json", arr.toString()).apply()
            }
            prefs.edit().putString("logged_in_name", newName).apply()
            Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
} 