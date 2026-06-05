package com.example.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.notes.nav.NotesNavGraph
import com.example.notes.ui.theme.NotesAppTheme
import com.example.notes.ui.viewmodel.NotesViewModel
import com.example.notes.ui.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val factory = ViewModelFactory((application as NotesApplication).repository)
        val viewModel: NotesViewModel by lazy {
            ViewModelProvider(this, factory)[NotesViewModel::class.java]
        }

        setContent {
            NotesAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NotesNavGraph(viewModel = viewModel)
                }
            }
        }
    }
}
