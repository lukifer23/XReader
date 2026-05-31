package com.xreader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.xreader.app.ui.XReaderApp

class MainActivity : FragmentActivity() {
    private var incomingIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingIntent = intent
        enableEdgeToEdge()
        setContent {
            XReaderApp(
                container = (application as XReaderApplication).container,
                incomingIntent = incomingIntent,
                onIncomingIntentConsumed = ::clearIncomingIntent
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntent = intent
    }

    private fun clearIncomingIntent(intent: Intent) {
        if (incomingIntent === intent) incomingIntent = null
        setIntent(Intent(Intent.ACTION_MAIN))
    }
}
