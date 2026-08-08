package com.kimbaeksoo.pdftoepub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kimbaeksoo.pdftoepub.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var selectedPdfUri: Uri? = null
    private var selectedPdfBaseName: String = "document"

    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) onPdfSelected(uri)
        }

    private val createEpubLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/epub+zip")) { uri ->
            if (uri != null) convertAndSave(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonSelectPdf.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("application/pdf"))
        }

        binding.buttonConvert.setOnClickListener {
            createEpubLauncher.launch("$selectedPdfBaseName.epub")
        }

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /** Supports being opened directly from a file manager / "share" menu with a PDF. */
    private fun handleIncomingIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) onPdfSelected(uri)
    }

    private fun onPdfSelected(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Not all URIs (e.g. from ACTION_SEND) support persistable permissions; safe to ignore.
        }

        selectedPdfUri = uri
        selectedPdfBaseName = queryDisplayName(uri)?.removeSuffix(".pdf")?.removeSuffix(".PDF")
            ?: "document"

        binding.textSelectedFile.text = queryDisplayName(uri) ?: uri.toString()
        binding.buttonConvert.isEnabled = true
        binding.textStatus.text = getString(R.string.status_idle)
        binding.progressBar.progress = 0
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme != "content") return uri.lastPathSegment
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(nameIndex)
                }
            }
        return null
    }

    private fun convertAndSave(destinationUri: Uri) {
        val sourceUri = selectedPdfUri ?: return
        setUiConverting(true)

        lifecycleScope.launch {
            try {
                val chapters = withContext(Dispatchers.IO) {
                    PdfTextExtractor(applicationContext).extract(sourceUri) { page, total ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            updateProgress(page, total)
                        }
                    }
                }

                binding.textStatus.text = getString(R.string.status_building_epub)

                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(destinationUri)?.use { out ->
                        EpubBuilder(title = selectedPdfBaseName, chapters = chapters).writeTo(out)
                    } ?: throw IllegalStateException("저장 위치를 열 수 없습니다.")
                }

                val savedName = queryDisplayName(destinationUri) ?: "$selectedPdfBaseName.epub"
                binding.textStatus.text = getString(R.string.status_done, savedName)
                Toast.makeText(this@MainActivity, getString(R.string.status_done, savedName), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                binding.textStatus.text = getString(R.string.status_error, e.message ?: e.toString())
            } finally {
                setUiConverting(false)
            }
        }
    }

    private fun updateProgress(page: Int, total: Int) {
        binding.textStatus.text = getString(R.string.status_extracting, page, total)
        binding.progressBar.max = total
        binding.progressBar.progress = page
    }

    private fun setUiConverting(isConverting: Boolean) {
        binding.buttonConvert.isEnabled = !isConverting
        binding.buttonSelectPdf.isEnabled = !isConverting
        binding.progressBar.visibility = if (isConverting) android.view.View.VISIBLE else android.view.View.INVISIBLE
        if (!isConverting) binding.progressBar.progress = 0
    }
}
