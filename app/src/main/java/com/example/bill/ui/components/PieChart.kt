package com.example.bill.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bill.data.CategoryTotal
import com.example.bill.ui.theme.PieColors

@Composable
fun PieChart(
    categoryTotals: List<CategoryTotal>,
    totalAmount: Long,
    modifier: Modifier = Modifier
) {
    if (categoryTotals.isEmpty() || totalAmount == 0L) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无数据", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        // Pie chart canvas
        Box(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val canvasSize = size.minDimension
                val strokeWidth = canvasSize * 0.4f
                val diameter = canvasSize - strokeWidth
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f
                )

                var startAngle = -90f

                for ((index, item) in categoryTotals.withIndex()) {
                    val sweepAngle = (item.total.toFloat() / totalAmount.toFloat()) * 360f
                    val colorIndex = index % PieColors.size
                    drawArc(
                        color = PieColors[colorIndex],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth)
                    )
                    startAngle += sweepAngle
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Legend
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categoryTotals.forEachIndexed { index, item ->
                val colorIndex = index % PieColors.size
                val percentage = if (totalAmount > 0) item.total.toFloat() / totalAmount.toFloat() * 100f else 0f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .width(12.dp)
                            .height(12.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(color = PieColors[colorIndex])
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format("%.2f (%.1f%%)", item.total / 100.0, percentage),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
