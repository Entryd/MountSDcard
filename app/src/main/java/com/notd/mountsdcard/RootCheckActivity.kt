package com.notd.mountsdcard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RootCheckActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_root_check)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        CoroutineScope(Dispatchers.IO).launch {
            val isRoot = Shell.getShell().isRoot

            withContext(Dispatchers.Main) {
                if (isRoot) {
                    startActivity(Intent(this@RootCheckActivity, MainActivity::class.java))
                    finish()
                } else {
                    progressBar.visibility = View.GONE
                    tvStatus.text = getString(R.string.error_root_denied)
                    tvStatus.setTextColor(android.graphics.Color.RED)
                }
            }
        }
    }
}