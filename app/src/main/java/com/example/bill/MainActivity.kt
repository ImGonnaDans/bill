package com.example.bill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bill.ui.BillApp
import com.example.bill.ui.theme.BillTheme
import com.example.bill.ui.viewmodel.BillViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BillTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: BillViewModel = viewModel()
                    BillApp(viewModel = viewModel)
                }
            }
        }
    }
}