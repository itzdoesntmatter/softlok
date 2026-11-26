// SPDX-License-Identifier: AGPL-3.0-or-later

package app.softlok

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.softlok.ui.SoftlokRoot
import app.softlok.ui.SoftlokTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SoftlokTheme {
                SoftlokRoot()
            }
        }
    }
}
