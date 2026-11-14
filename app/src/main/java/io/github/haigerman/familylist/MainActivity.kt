package io.github.haigerman.familylist

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.haigerman.familylist.data.Store
import io.github.haigerman.familylist.notify.Reminders
import io.github.haigerman.familylist.ui.FamilyListTheme
import io.github.haigerman.familylist.ui.MainScreen
import io.github.haigerman.familylist.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    /** Zählt hoch, wenn eine Erinnerung angetippt wurde: dann zeigt die App die Tasks. */
    private var openTasks by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        enableEdgeToEdge()
        if (savedInstanceState == null && intent?.getBooleanExtra(Reminders.EXTRA_OPEN_TASKS, false) == true) openTasks++
        setContent {
            FamilyListTheme {
                var settings by rememberSaveable { mutableStateOf(false) }
                if (settings) {
                    SettingsScreen(onBack = {
                        settings = false
                        Store.requestSync()
                    })
                } else {
                    MainScreen(openTasks = openTasks, onSettings = { settings = true })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(Reminders.EXTRA_OPEN_TASKS, false)) openTasks++
    }
}
