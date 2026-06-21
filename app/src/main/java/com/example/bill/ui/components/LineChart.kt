package com.example.bill.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bill.data.DailyTotal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun LineChart(
    dailyTotals: List<DailyTotal>,
    year: Int,
    month: Int,
    modifier: Modifier = Modifier
) {
    if (dailyTotals.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Text("暂无数据", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        }
        return
    }

    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
    }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    // Build a map of day -> total
    val dayTotalsMap = mutableMapOf<Int, Long>()
    for (dt in dailyTotals) {
        val dayCal = Calendar.getInstance().apply { timeInMillis = dt.dayStamp * 86400000 }
        val day = dayCal.get(Calendar.DAY_OF_MONTH)
        dayTotalsMap[day] = dt.total
    }

    // Fill in all days of month
    val values = FloatArray(daysInMonth) { dayIndex ->
        dayTotalsMap[dayIndex + 1]?.toFloat() ?: 0f
    }

    val maxVal = values.maxOrNull()?.let { if (it > 0f) it else 1f } ?: 1f

    val lineColor = Color(0xFF4CAF50)
    val pointColor = Color(0xFF388E3C)
    val gridColor = Color(0xFFE0E0E0)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(start = 40.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
    ) {
        val w = size.width
        val h = size.height
        val paddingLeft = 0f
        val paddingRight = 0f
        val paddingTop = 8f
        val paddingBottom = 0f

        val chartW = w - paddingLeft - paddingRight
        val chartH = h - paddingTop - paddingBottom

        if (daysInMonth <= 1) return@Canvas

        val stepX = chartW / (daysInMonth - 1)

        // Draw horizontal grid lines
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = paddingTop + chartH * (1f - i.toFloat() / gridLines)
            drawLine(
                color = gridColor,
                start = Offset(paddingLeft, y),
                end = Offset(paddingLeft + chartW, y),
                strokeWidth = 1f
            )

            // Y-axis label
            val labelValue = maxVal * i / gridLines
            drawContext.canvas.nativeCanvas.drawText(
                String.format("%.0f", labelValue / 100.0),
                -35f,
                y + 4f,
                android.graphics.Paint().apply {
                    this.color = android.graphics.Color.GRAY
                    this.textSize = 22f
                    this.textAlign = android.graphics.Paint.Align.LEFT
                }
            )
        }

        // Build points
        val points = mutableListOf<Pair<Float, Float>>()
        for (day in 0 until daysInMonth) {
            val x = paddingLeft + day * stepX
            val y = paddingTop + chartH * (1f - values[day] / maxVal)
            points.add(Pair(x, y))
        }

        // Draw the line path
        if (points.size >= 2) {
            val path = Path()
            path.moveTo(points[0].first, points[0].second)
            for (i in 1 until points.size) {
                path.lineTo(points[i].first, points[i].second)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // Draw scatter points
        points.forEachIndexed { day, (x, y) ->
            if (values[day] > 0f) {
                drawCircle(
                    color = pointColor,
                    radius = 5f,
                    center = Offset(x, y)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3f,
                    center = Offset(x, y)
                )
            }
        }

        // Draw X-axis day labels (every 5 days)
        for (day in 0 until daysInMonth) {
            if (day % 5 == 0 || day == daysInMonth - 1) {
                val x = paddingLeft + day * stepX
                val label = "${day + 1}"
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x,
                    h - 2f,
                    android.graphics.Paint().apply {
                        this.color = android.graphics.Color.GRAY
                        this.textSize = 22f
                        this.textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }
    }
}
