package io.wenyou.textquest

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import io.wenyou.textquest.ui.WenYouAppRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as WenYouApp
        setContent {
            WenYouAppRoot(app.container, showStartup = true)
        }
        if (savedInstanceState == null) receiveShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveShare(intent)
    }

    /** Shared text, opened .wenyou files and share links all go to the share inbox for a preview. */
    private fun receiveShare(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_VIEW) return
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    when (action) {
                        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                            ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(::readSmallText)
                        else -> intent.data?.let { if (it.scheme == "content" || it.scheme == "file") readSmallText(it) else it.toString() }
                    }
                }.getOrNull()
            }
            if (text == null || !(application as WenYouApp).container.shareInbox.offer(text)) {
                Toast.makeText(this@MainActivity, io.wenyou.textquest.ui.common.tr("没有找到可导入的分享内容"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun readSmallText(uri: Uri): String? = contentResolver.openInputStream(uri)?.use { input ->
        val bytes = input.readBytes(MAX_SHARE_FILE)
        bytes?.toString(Charsets.UTF_8)
    }

    private fun java.io.InputStream.readBytes(limit: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > limit) return null
            out.write(buffer, 0, n)
        }
    }

    private companion object {
        const val MAX_SHARE_FILE = 20 * 1024 * 1024
    }
}
