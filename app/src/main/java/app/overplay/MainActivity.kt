// SPDX-License-Identifier: AGPL-3.0-or-later

package app.overplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.overplay.ui.OverplayRoot
import app.overplay.ui.OverplayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OverplayTheme {
                OverplayRoot()
            }
        }
    }
}
