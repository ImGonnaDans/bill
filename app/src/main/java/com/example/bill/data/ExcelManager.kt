package com.example.bill.data

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ExcelRow(
    val type: BillType,
    val category: String,
    val amountInCents: Long,
    val dateMillis: Long,
    val note: String
)

class ExcelManager {

    fun exportToExcel(bills: List<Bill>, outputStream: OutputStream) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINESE)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINESE)

        // Build rows: header + data
        val headers = listOf("序号", "记账日期", "记账时间", "分类", "支出或收入", "金额", "流出账户", "流入账户", "备注", "图片")
        val rows = mutableListOf<List<String>>()

        // Sort bills by date ascending
        val sortedBills = bills.sortedBy { it.dateMillis }

        sortedBills.forEachIndexed { index, bill ->
            val dateStr = dateFormat.format(Date(bill.dateMillis))
            val timeStr = timeFormat.format(Date(bill.dateMillis))
            val typeStr = if (bill.type == BillType.EXPENSE) "支出" else "收入"
            val amountStr = String.format("%.2f", bill.amountInCents / 100.0)

            rows.add(
                listOf(
                    (index + 1).toString(),
                    dateStr,
                    timeStr,
                    bill.category,
                    typeStr,
                    amountStr,
                    "",
                    "",
                    bill.note,
                    ""
                )
            )
        }

        writeXlsx(outputStream, "账单", headers, rows)
    }

    fun importFromExcel(inputStream: InputStream): ImportResult {
        val rows = readXlsx(inputStream)
        return parseRowList(rows)
    }

    fun importFromExcelBytes(bytes: ByteArray): ImportResult {
        if (bytes.size < 4) {
            return ImportResult(emptyList(), listOf("文件太小或为空"))
        }
        val magic = bytes.joinToString("") { "%02x".format(it) }.take(8)
        Log.d("XLSX_READ", "importFromExcelBytes: magic=$magic, size=${bytes.size}")
        return when {
            magic.startsWith("504b0304") -> {
                Log.d("XLSX_READ", "Detected .xlsx (ZIP) format")
                importFromExcel(ByteArrayInputStream(bytes))
            }
            magic.startsWith("d0cf11e0") -> {
                Log.d("XLSX_READ", "Detected .xls (OLE2) format, using POI HSSF")
                val rows = readXlsBytes(bytes)
                parseRowList(rows)
            }
            else -> {
                Log.e("XLSX_READ", "Unknown format magic=$magic")
                ImportResult(emptyList(), listOf("不支持的文件格式，请使用 .xlsx 或 .xls 文件"))
            }
        }
    }

    private fun readXlsBytes(bytes: ByteArray): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        try {
            val workbook = org.apache.poi.hssf.usermodel.HSSFWorkbook(ByteArrayInputStream(bytes))
            val sheet = workbook.getSheetAt(0)
            val formatter = org.apache.poi.ss.usermodel.DataFormatter()
            for (row in sheet) {
                val rowValues = mutableListOf<String>()
                for (cell in row) {
                    rowValues.add(formatter.formatCellValue(cell))
                }
                rows.add(rowValues)
            }
            workbook.close()
        } catch (e: Exception) {
            Log.e("XLSX_READ", "HSSF parse failed: ${e.message}")
        }
        Log.d("XLSX_READ", "readXlsBytes: ${rows.size} rows")
        return rows
    }

    private fun parseRowList(rows: List<List<String>>): ImportResult {
        val validRows = mutableListOf<ExcelRow>()
        val errors = mutableListOf<String>()
        val tag = "PARSE_ROW"

        var startIndex = 0
        if (rows.isNotEmpty()) {
            val firstRow = rows[0]
            if (firstRow.isNotEmpty() && (firstRow[0] == "序号" || firstRow[0].toIntOrNull() == null)) {
                startIndex = 1
            }
        }

        val dateFormat1 = SimpleDateFormat("yyyy-MM-dd", Locale.CHINESE)
        val dateFormat2 = SimpleDateFormat("yyyy/MM/dd", Locale.CHINESE)
        val dateFormat3 = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINESE)

        for (i in startIndex until rows.size) {
            val row = rows[i]
            if (row.isEmpty() || row.all { it.isBlank() }) continue
            val rawRowStr = row.joinToString(" | ")
            try {
                val dateStr = row.getOrElse(1) { "" }.trim()
                val timeStr = row.getOrElse(2) { "" }.trim()
                val category = row.getOrElse(3) { "" }.trim()
                val typeStr = row.getOrElse(4) { "" }.trim()
                val amountStr = row.getOrElse(5) { "" }.trim()
                val note = row.getOrElse(8) { "" }.trim()

                if (dateStr.isBlank() || category.isBlank() || typeStr.isBlank() || amountStr.isBlank()) {
                    Log.w(tag, "行${i + 1}: 缺少必要字段 raw=[$rawRowStr]")
                    errors.add("第${i + 1}行: 缺少必要字段 [$rawRowStr]"); continue
                }

                val type = when (typeStr) {
                    "支出" -> BillType.EXPENSE; "收入" -> BillType.INCOME
                    else -> {
                        Log.w(tag, "行${i + 1}: 未知类型 typeStr='$typeStr' raw=[$rawRowStr]")
                        errors.add("第${i + 1}行: 未知类型 '$typeStr'"); continue
                    }
                }

                var date: Date? = null
                for (fmt in listOf(dateFormat1, dateFormat2, dateFormat3)) {
                    try { date = fmt.parse(dateStr); if (date != null) break } catch (_: Exception) {}
                }
                if (date == null) {
                    Log.w(tag, "行${i + 1}: 无法解析日期 dateStr='$dateStr' raw=[$rawRowStr]")
                    errors.add("第${i + 1}行: 无法解析日期 '$dateStr'"); continue
                }

                val cal = Calendar.getInstance().apply { time = date }
                if (timeStr.isNotBlank()) {
                    try {
                        val parts = timeStr.split(":")
                        if (parts.size >= 2) {
                            cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                            cal.set(Calendar.MINUTE, parts[1].toInt())
                            cal.set(Calendar.SECOND, if (parts.size >= 3) parts[2].toInt() else 0)
                            cal.set(Calendar.MILLISECOND, 0)
                        }
                    } catch (_: Exception) {}
                }

                val amount = amountStr.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Log.w(tag, "行${i + 1}: 无效金额 amountStr='$amountStr' raw=[$rawRowStr]")
                    errors.add("第${i + 1}行: 无效金额 '$amountStr'"); continue
                }
                val amountInCents = (amount * 100 + 0.5).toLong()

                validRows.add(ExcelRow(type, category, amountInCents, cal.timeInMillis, note))
            } catch (e: Exception) {
                Log.w(tag, "行${i + 1}: 解析异常 raw=[$rawRowStr] ex=${e.message}")
                errors.add("第${i + 1}行: 解析错误 - ${e.message}")
            }
        }
        Log.d(tag, "parseRowList done: ${validRows.size} valid, ${errors.size} errors")
        return ImportResult(validRows, errors)
    }

    data class ImportResult(
        val rows: List<ExcelRow>,
        val errors: List<String>
    )

    // ===== XLSX Writer =====

    private fun writeXlsx(outputStream: OutputStream, sheetName: String, headers: List<String>, rows: List<List<String>>) {
        val allStrings = mutableListOf<String>()
        val stringIndex = mutableMapOf<String, Int>()

        fun getOrCreateIndex(text: String): Int {
            return stringIndex.getOrPut(text) {
                allStrings.add(text)
                allStrings.size - 1
            }
        }

        // Pre-register header strings
        headers.forEach { getOrCreateIndex(it) }

        // Pre-register all data strings
        for (row in rows) {
            for (cell in row) {
                getOrCreateIndex(cell)
            }
        }

        ZipOutputStream(outputStream).use { zip ->

            // [Content_Types].xml
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                appendLine("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                appendLine("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                appendLine("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                appendLine("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                appendLine("<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
                appendLine("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
                appendLine("<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>")
                append("</Types>")
            }.toByteArray())

            // _rels/.rels
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                appendLine("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
                appendLine("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>")
                append("</Relationships>")
            }.toByteArray())

            // xl/workbook.xml
            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            zip.write(buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                appendLine("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
                appendLine("<sheets>")
                appendLine("<sheet name=\"${xmlEscape(sheetName)}\" sheetId=\"1\" r:id=\"rId1\"/>")
                appendLine("</sheets>")
                append("</workbook>")
            }.toByteArray())

            // xl/_rels/workbook.xml.rels
            zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zip.write(buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                appendLine("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
                appendLine("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>")
                appendLine("<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
                appendLine("<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>")
                append("</Relationships>")
            }.toByteArray())

            // xl/styles.xml (minimal)
            zip.putNextEntry(ZipEntry("xl/styles.xml"))
            zip.write(buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                append("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"/>")
            }.toByteArray())

            // xl/sharedStrings.xml
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write(buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                appendLine("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"${allStrings.size}\" uniqueCount=\"${allStrings.size}\">")
                for (s in allStrings) {
                    append("<si><t>")
                    append(xmlEscape(s))
                    appendLine("</t></si>")
                }
                append("</sst>")
            }.toByteArray())

            // xl/worksheets/sheet1.xml
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                appendLine("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                appendLine("<sheetData>")

                val colCount = headers.size

                // Header row (row 1)
                append("<row r=\"1\">")
                for (col in 0 until colCount) {
                    val ref = "${columnLetter(col)}1"
                    val idx = getOrCreateIndex(headers[col])
                    append("<c r=\"$ref\" t=\"s\"><v>$idx</v></c>")
                }
                appendLine("</row>")

                // Data rows
                rows.forEachIndexed { rowIndex, row ->
                    val rowNum = rowIndex + 2
                    append("<row r=\"$rowNum\">")
                    for (col in 0 until colCount) {
                        val ref = "${columnLetter(col)}$rowNum"
                        val cellValue = row.getOrElse(col) { "" }
                        if (cellValue.isNotBlank()) {
                            val idx = getOrCreateIndex(cellValue)
                            append("<c r=\"$ref\" t=\"s\"><v>$idx</v></c>")
                        }
                    }
                    appendLine("</row>")
                }

                appendLine("</sheetData>")
                append("</worksheet>")
            }.toByteArray())
        }
    }

    // ===== XLSX Reader =====

    private fun readXlsx(inputStream: InputStream): List<List<String>> {
        val sharedStrings = mutableListOf<String>()
        val rows = mutableListOf<List<String>>()
        val zipEntries = mutableMapOf<String, ByteArray>()

        val tag = "XLSX_READ"

        // Read all ZIP entries into memory
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            var entryCount = 0
            while (entry != null) {
                entryCount++
                val name = entry.name
                Log.d(tag, "ZIP entry #$entryCount: name='$name' isDirectory=${entry.isDirectory} size=${entry.size}")
                if (!entry.isDirectory) {
                    val bytes = zip.readBytes()
                    Log.d(tag, "  -> read ${bytes.size} bytes for '$name'")
                    zipEntries[name] = bytes
                } else {
                    Log.d(tag, "  -> skipped directory entry")
                }
                entry = zip.nextEntry
            }
            Log.d(tag, "ZIP done: $entryCount entries total, ${zipEntries.size} non-dir entries stored")
        }

        if (zipEntries.isEmpty()) {
            Log.e(tag, "FATAL: no entries found in ZIP! Stream problem?")
            return emptyList()
        }
        Log.d(tag, "ZIP entry keys: ${zipEntries.keys.joinToString(", ")}")

        // Try find sheet file — different xlsx tools use different entry names
        val sheetEntryNames = listOf(
            "xl/worksheets/sheet1.xml",
            "xl/worksheets/Sheet1.xml",
            "/xl/worksheets/sheet1.xml",
            "xl/worksheets/sheet.xml"
        )
        val sheetEntryName = sheetEntryNames.firstOrNull { zipEntries.containsKey(it) }
        val sheetBytes = sheetEntryName?.let { zipEntries[it] }

        if (sheetBytes == null) {
            Log.e(tag, "FATAL: no sheet1.xml found in ZIP! Tried: ${sheetEntryNames.joinToString(", ")}")
            return emptyList()
        }
        Log.d(tag, "Found sheet at: '$sheetEntryName', size=${sheetBytes.size} bytes")

        // Parse shared strings
        val ssEntryNames = listOf(
            "xl/sharedStrings.xml",
            "xl/SharedStrings.xml",
            "/xl/sharedStrings.xml"
        )
        val ssEntryName = ssEntryNames.firstOrNull { zipEntries.containsKey(it) }
        val ssBytes = ssEntryName?.let { zipEntries[it] }

        if (ssBytes != null) {
            Log.d(tag, "Found sharedStrings at: '$ssEntryName', size=${ssBytes.size} bytes")

            // Try XmlPullParser with namespaces OFF to avoid namespace prefix issues
            try {
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(java.io.InputStreamReader(java.io.ByteArrayInputStream(ssBytes), "UTF-8"))
                var eventType = parser.eventType
                var inSi = false
                var inT = false

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            val tagName = parser.name
                            if (tagName == "si") inSi = true
                            if (inSi && tagName == "t") inT = true
                        }
                        XmlPullParser.TEXT -> {
                            if (inT) {
                                sharedStrings.add(parser.text)
                                Log.v(tag, "SS parsed: '${parser.text.take(30)}'")
                                inT = false
                                inSi = false
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == "si") inSi = false
                        }
                    }
                    eventType = parser.next()
                }
                Log.d(tag, "XmlPullParser parsed ${sharedStrings.size} shared strings")
            } catch (e: Exception) {
                Log.e(tag, "XmlPullParser failed: ${e.message}")
                sharedStrings.clear()
            }
        } else {
            Log.w(tag, "No sharedStrings.xml found — data may use inline strings")
        }

        // Fallback for shared strings: regex
        if (sharedStrings.isEmpty() && ssBytes != null) {
            Log.d(tag, "Trying regex fallback for shared strings...")
            try {
                val text = String(ssBytes, Charsets.UTF_8)
                Log.v(tag, "SS raw first 300 chars: ${text.take(300)}")
                val regex = Regex("<t[^>]*>([^<]*)</t>", RegexOption.DOT_MATCHES_ALL)
                for (match in regex.findAll(text)) {
                    sharedStrings.add(match.groupValues[1])
                }
                Log.d(tag, "Regex fallback parsed ${sharedStrings.size} shared strings")
            } catch (e: Exception) {
                Log.e(tag, "Regex fallback failed: ${e.message}")
            }
        }

        // Parse sheet data using regex
        Log.d(tag, "Parsing sheet data with regex...")
        try {
            val text = String(sheetBytes, Charsets.UTF_8)
            Log.v(tag, "Sheet raw first 400 chars: ${text.take(400)}")
            Log.v(tag, "Sheet raw last 300 chars: ${text.takeLast(300)}")

            val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
            val cellRegex = Regex("<c[^>]*>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
            val valueRegex = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)

            val rowMatches = rowRegex.findAll(text).toList()
            Log.d(tag, "Regex found ${rowMatches.size} row elements")

            for ((rowIdx, rowMatch) in rowMatches.withIndex()) {
                val rowContent = rowMatch.groupValues[1]
                val rowValues = mutableListOf<String>()

                for (cellMatch in cellRegex.findAll(rowContent)) {
                    val fullCell = cellMatch.groupValues[0]
                    val cellContent = cellMatch.groupValues[1]

                    // Check if cell type is 's' (shared string)
                    val typeMatch = Regex(" t=\"([^\"]+)\"").find(fullCell)
                    val isString = typeMatch?.groupValues?.get(1) == "s"

                    val valueMatch = valueRegex.find(cellContent)
                    val rawValue = valueMatch?.groupValues?.get(1) ?: ""

                    if (isString && rawValue.isNotBlank()) {
                        val idx = rawValue.toIntOrNull()
                        if (idx != null && idx < sharedStrings.size) {
                            rowValues.add(sharedStrings[idx])
                        } else {
                            rowValues.add(rawValue)
                        }
                    } else if (rawValue.isNotBlank()) {
                        // Also try to resolve shared string index even without t="s"
                        // (some xlsx writers omit the type attribute for strings)
                        val idx = rawValue.toIntOrNull()
                        if (idx != null && idx < sharedStrings.size) {
                            rowValues.add(sharedStrings[idx])
                        } else {
                            rowValues.add(rawValue)
                        }
                    }
                }

                Log.v(tag, "Row $rowIdx: ${rowValues.joinToString(", ")}")
                rows.add(rowValues)
            }
        } catch (e: Exception) {
            Log.e(tag, "Sheet regex parsing failed: ${e.message}")
        }

        Log.d(tag, "readXlsx done: ${rows.size} rows, ${sharedStrings.size} shared strings")
        return rows
    }

    // ===== Helpers =====

    private fun columnLetter(col: Int): String {
        val sb = StringBuilder()
        var c = col
        while (c >= 0) {
            sb.insert(0, ('A'.code + (c % 26)).toChar())
            c = c / 26 - 1
        }
        return sb.toString()
    }

    private fun xmlEscape(s: String): String {
        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
