package com.example.predictsafe

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

class ReportActivity : AppCompatActivity() {
    private lateinit var reportRecyclerView: RecyclerView
    private lateinit var adapter: ReportAdapter
    private var reports: List<UserReport> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        val toolbar = findViewById<Toolbar>(R.id.reportToolbar)
        toolbar.title = "Previous Reports"
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { finish() }

        reportRecyclerView = findViewById(R.id.reportRecyclerView)
        reportRecyclerView.layoutManager = LinearLayoutManager(this)
        reports = loadUserReports()
        adapter = ReportAdapter(reports)
        reportRecyclerView.adapter = adapter
    }

    private fun loadUserReports(): List<UserReport> {
        val prefs = getSharedPreferences("emergency_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("user_reports_json", null)
        val list = mutableListOf<UserReport>()
        if (json != null) {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(UserReport(obj.getString("text"), obj.getLong("timestamp")))
            }
        }
        return list.reversed() // newest first
    }

    class ReportAdapter(private val reports: List<UserReport>) : RecyclerView.Adapter<ReportAdapter.ReportViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_report, parent, false)
            return ReportViewHolder(view)
        }
        override fun getItemCount(): Int = reports.size
        override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
            holder.bind(reports[position])
        }
        class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val timestampView: TextView = itemView.findViewById(R.id.reportTimestamp)
            private val textView: TextView = itemView.findViewById(R.id.reportText)
            fun bind(report: UserReport) {
                val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                timestampView.text = sdf.format(Date(report.timestamp))
                textView.text = report.text
            }
        }
    }
} 