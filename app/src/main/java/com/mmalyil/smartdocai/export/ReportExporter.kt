package com.mmalyil.smartdocai.export


import android.content.Context
import android.net.Uri
import com.mmalyil.smartdocai.api.ChatProxyService
import com.mmalyil.smartdocai.model.ChatMessage
import com.mmalyil.smartdocai.model.ChatRequest
import com.mmalyil.smartdocai.model.ChatUnifiedResponse
import com.mmalyil.smartdocai.util.GPTUsageManager

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.time.LocalDateTime

/**
 * Handles XLSX → DOCX exports with optional AI insights.
 */
object ReportExporter {

    // --- Data model ---
    data class SheetData(
        val name: String,
        val headers: List<String>,
        val rows: List<List<String>>
    )

    // --- Step 1: Parse XLSX into structured data ---
    fun parseXlsxToSheets(input: InputStream, maxRowsPerSheet: Int = 5000): List<SheetData> {
        XSSFWorkbook(input).use { wb ->
            val sheets = mutableListOf<SheetData>()
            val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            for (i in 0 until wb.numberOfSheets) {
                val sheet = wb.getSheetAt(i) ?: continue
                val iterator = sheet.iterator()
                if (!iterator.hasNext()) continue

                // Header row
                val headerRow = iterator.next()
                val headers = (0 until headerRow.lastCellNum).map { c ->
                    headerRow.getCell(c)?.toString()?.trim().takeUnless { it.isNullOrBlank() }
                        ?: "Column ${c + 1}"
                }

                val rows = ArrayList<List<String>>()
                var count = 0
                while (iterator.hasNext() && count < maxRowsPerSheet) {
                    val r = iterator.next()
                    val values = (0 until headerRow.lastCellNum).map { c ->
                        val cell = r.getCell(c)
                        when {
                            cell == null -> ""
                            cell.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC &&
                                    org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell) ->
                                dateFmt.format(cell.dateCellValue)
                            else -> cell.toString().trim()
                        }
                    }
                    rows.add(values)
                    count++
                }
                sheets.add(SheetData(sheet.sheetName ?: "Sheet ${i + 1}", headers, rows))
            }
            return sheets
        }
    }

    // --- Step 2: Build AI insights prompt ---
    fun buildInsightsPrompt(sheets: List<SheetData>, rowsPreview: Int = 10): String {
        val sb = StringBuilder()
        sb.append("You are a concise data analyst. Given Excel previews, write 4–6 bullet insights:\n\n")
        sheets.forEach { s ->
            sb.append("### ${s.name}\n")
            sb.append("Headers: ${s.headers.joinToString()}\n")
            s.rows.take(rowsPreview).forEach { row ->
                sb.append(row.joinToString(" | ")).append('\n')
            }
            sb.append('\n')
        }
        sb.append("Return plain text bullets (no markdown tables).\n")
        return sb.toString()
    }


    private fun ChatUnifiedResponse.asPlainText(): String {
        // Try common shapes first, then fallbacks
        // 1) unified `text`
        try {
            val f = this::class.java.getDeclaredField("text")
            f.isAccessible = true
            val v = f.get(this) as? String
            if (!v.isNullOrBlank()) return v
        } catch (_: Exception) {}

        // 2) OpenAI-style choices[0].message.content
        try {
            val choicesField = this::class.java.getDeclaredField("choices")
            choicesField.isAccessible = true
            val choices = choicesField.get(this) as? List<*>
            val first = choices?.firstOrNull()
            val msgField = first?.javaClass?.getDeclaredField("message")
            msgField?.isAccessible = true
            val message = msgField?.get(first)
            val contentField = message?.javaClass?.getDeclaredField("content")
            contentField?.isAccessible = true
            val content = contentField?.get(message) as? String
            if (!content.isNullOrBlank()) return content
        } catch (_: Exception) {}

        // 3) Fallbacks (e.g., `reply`, `output`, etc.)
        for (name in listOf("reply", "output", "result")) {
            try {
                val f = this::class.java.getDeclaredField(name)
                f.isAccessible = true
                val v = f.get(this) as? String
                if (!v.isNullOrBlank()) return v
            } catch (_: Exception) {}
        }
        return ""
    }



    suspend fun fetchAiInsights(
        proxy: ChatProxyService,
        prompt: String,
        model: String
    ): String {
        val req = ChatRequest(
            model = model, // e.g., "gpt-4o" or "llama3-8b-8192"
            messages = listOf(ChatMessage(role = "user", content = prompt))
        )

        val resp: ChatUnifiedResponse = proxy.getChatReply(req)

        // Prefer the flattened content field
        val content = resp.content
        if (content.isNotBlank()) {
            return content.take(3000) // safety cap
        }

        // Optional fallback: try to extract from raw JSON if content is blank
        return tryExtractFromRaw(resp.raw).take(3000)
    }

    // --- Step 4: Build DOCX report ---
    fun createXlsxAiReportDocx(
        context: Context,
        fileTitle: String,
        sheets: List<SheetData>,
        aiInsights: String
    ): File {
        val doc = XWPFDocument()

        // Title
        val titlePara = doc.createParagraph()
        titlePara.createRun().apply {
            setText("$fileTitle — AI Report")
            isBold = true
            fontSize = 16
        }

        val metaPara = doc.createParagraph()
        metaPara.createRun().apply {
            setText("Generated by SmartDocAI • ${LocalDateTime.now()}")
            isItalic = true
        }

        // AI Insights
        val insightsHeader = doc.createParagraph()
        insightsHeader.createRun().apply {
            setText("AI Insights")
            isBold = true
            fontSize = 14
        }
        aiInsights.lines().forEach { line ->
            val p = doc.createParagraph()
            p.createRun().setText(line)
        }

        // Tables for each sheet
        sheets.forEach { s ->
            val h2 = doc.createParagraph()
            h2.createRun().apply {
                setText("Sheet: ${s.name}")
                isBold = true
                fontSize = 13
            }

            val table = doc.createTable()
            // Header row
            val headerRow = table.getRow(0)
            headerRow.getCell(0).setText(s.headers.getOrNull(0) ?: "")
            for (c in 1 until s.headers.size) {
                headerRow.addNewTableCell().setText(s.headers[c])
            }

            // Data rows (cap for performance)
            val maxRowsOut = 2000
            s.rows.take(maxRowsOut).forEach { row ->
                val r = table.createRow()
                for (c in row.indices) {
                    val cell = if (c < r.tableCells.size) r.getCell(c) else r.addNewTableCell()
                    cell.removeParagraph(0)
                    val p = cell.addParagraph()
                    p.createRun().setText(row[c])
                }
            }
        }

        // Save under externalFilesDir/exports — matches FileProvider paths + Files history.
        val outDir = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "exports"
        ).apply { mkdirs() }
        val safeTitle = fileTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
        val outFile = File(outDir, "${safeTitle}_AI_Report_${System.currentTimeMillis()}.docx")
        FileOutputStream(outFile).use { doc.write(it) }
        doc.close()
        return outFile
    }


    // ReportExporter.kt (top or a small companion object)
    private data class ModelChoice(val apiModel: String, val usageKey: String)

    private fun selectModelForReport(context: Context): ModelChoice {
        // Adjust limits to your real caps
        return when {
            com.mmalyil.smartdocai.util.GPTUsageManager.getUsage(context, "gpt-4") < 300_000 ->
                ModelChoice(apiModel = "gpt-4o", usageKey = "gpt-4")
            com.mmalyil.smartdocai.util.GPTUsageManager.getUsage(context, "groq") < 500 ->
                ModelChoice(apiModel = "llama3-8b-8192", usageKey = "groq")
            else ->
                ModelChoice(apiModel = "gpt-3.5-turbo", usageKey = "gpt-3.5")
        }
    }
    // ReportExporter.kt
    // ReportExporter.kt
    suspend fun generateXlsxAiReport(
        context: Context,
        srcUri: Uri,
        titleHint: String,
        proxy: ChatProxyService,
        customPrompt: String? = null   // ✅ NEW (optional)
    ): Result<File> = runCatching {
        context.contentResolver.openInputStream(srcUri).use { input ->
            requireNotNull(input) { "Cannot open file" }
            val sheets = parseXlsxToSheets(input)

            // ✅ Use caller's prompt if provided; otherwise build default insights prompt
            // ✅ If user gave a custom prompt, wrap it *with* the sheet preview.
//    Otherwise use the default insights prompt.
            val promptToUse = if (!customPrompt.isNullOrBlank()) {
                buildCustomPrompt(customPrompt, sheets)
            } else {
                buildInsightsPrompt(sheets)
            }

            // 🔹 Choose model based on usage (same logic you had)
            val (apiModel, usageKey) = when {
                GPTUsageManager.getUsage(context, "gpt-4") < 300_000 -> "gpt-4o" to "gpt-4"
                GPTUsageManager.getUsage(context, "groq")  < 500      -> "llama3-8b-8192" to "groq"
                else                                                 -> "gpt-3.5-turbo" to "gpt-3.5"
            }

            // 🔹 Fetch AI insights with selected model + chosen prompt
            val insights = fetchAiInsights(proxy, promptToUse, apiModel)

            // 🔹 Record usage count under the chosen bucket
            GPTUsageManager.incrementUsage(context, usageKey)

            // 🔹 Create DOCX report
            createXlsxAiReportDocx(
                context = context,
                fileTitle = titleHint.ifBlank { "Report" },
                sheets = sheets,
                aiInsights = insights.ifBlank { "No strong insights were detected from the preview." }
            )
        }
    }

    private fun tryExtractFromRaw(raw: String): String {
        val key = "\"content\":\""
        val start = raw.indexOf(key)
        if (start == -1) return ""
        val from = start + key.length
        val end = raw.indexOf('"', from)
        return if (end > from) raw.substring(from, end) else ""
    }

    fun buildCustomPrompt(userPrompt: String, sheets: List<SheetData>, rowsPreview: Int = 10): String {
        val sb = StringBuilder()

        sb.appendLine("You are a concise data analyst.")
        sb.appendLine("The user’s question is:")
        sb.appendLine()
        sb.appendLine(userPrompt.trim())
        sb.appendLine()
        sb.appendLine("Use ONLY the following Excel data preview when answering. Do not invent data.")
        sb.appendLine()

        sheets.forEach { s ->
            sb.appendLine("### Sheet: ${s.name}")
            sb.appendLine("Headers: ${s.headers.joinToString()}")
            s.rows.take(rowsPreview).forEach { row ->
                sb.appendLine(row.joinToString(" | "))
            }
            sb.appendLine()
        }

        sb.appendLine("Answer directly and only based on this data. If something is not visible in the preview, say so explicitly.")
        return sb.toString()
    }


}