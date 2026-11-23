package io.github.haigerman.familylist

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.haigerman.familylist.data.Store
import io.github.haigerman.familylist.notify.Reminders
import io.github.haigerman.familylist.ui.FamilyListTheme
import io.github.haigerman.familylist.ui.MainScreen
import io.github.haigerman.familylist.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    /**
     * Wurde eine Benachrichtigung angetippt: welcher Reiter gezeigt werden soll (Tasks oder
     * Geburtstage) und eine laufende Nummer, damit auch ein zweites Antippen wirkt.
     */
    private var openPage by mutableStateOf(0 to -1)

    private fun handle(intent: Intent?) {
        val page = when {
            intent == null -> return
            intent.hasExtra(Reminders.EXTRA_OPEN_PAGE) -> intent.getIntExtra(Reminders.EXTRA_OPEN_PAGE, 1)
            intent.getBooleanExtra(Reminders.EXTRA_OPEN_TASKS, false) -> 1
            else -> return
        }
        openPage = (openPage.first + 1) to page
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            FamilyListTheme {
                var settings by rememberSaveable { mutableStateOf(false) }
                if (settings) {
                    SettingsScreen(onBack = {
                        settings = false
                        Store.requestSync()
                    })
                } else {
                    MainScreen(openPage = openPage, onSettings = { settings = true })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }
}
