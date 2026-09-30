package com.family.shoppinglist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.shoppinglist.ui.AppRoot
import com.family.shoppinglist.ui.AppViewModel
import com.family.shoppinglist.ui.theme.ShoppingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShoppingTheme {
                AppRoot(viewModel(factory = AppViewModel.Factory))
            }
        }
    }
}
