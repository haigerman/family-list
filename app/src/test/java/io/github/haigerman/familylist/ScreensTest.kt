package io.github.haigerman.familylist

import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.haigerman.familylist.data.FamilyData
import io.github.haigerman.familylist.data.Member
import io.github.haigerman.familylist.data.ShopItem
import io.github.haigerman.familylist.data.Store
import io.github.haigerman.familylist.data.Task
import io.github.haigerman.familylist.ui.Backdrop
import io.github.haigerman.familylist.ui.FamilyListTheme
import io.github.haigerman.familylist.ui.SettingsScreen
import io.github.haigerman.familylist.ui.ShopTab
import io.github.haigerman.familylist.ui.TaskEditor
import io.github.haigerman.familylist.ui.TasksTab
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Zeichnet die Bildschirme mit Beispieldaten – schlägt fehl, wenn einer beim Aufbau abstürzt.
 * Die Bilder landen unter app/build/screens/.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ScreensTest {
    private val today = LocalDate.now()
    private val data = FamilyData(
        shop = listOf(
            ShopItem("1", "Brot", true, "David"),
            ShopItem("2", "Milch (2 l)", false, "Anna"),
            ShopItem("3", "Äpfel", false, "David"),
            ShopItem("4", "Spülmaschinentabs, die guten ohne Plastikfolie drumherum", false, "Mia"),
        ),
        tasks = listOf(
            Task("1", "Altpapier wegbringen", true, listOf("David")),
            Task("2", "Geschenk für Oma besorgen", false, emptyList(), null, "Sie wünscht sich ein Buch"),
            Task("3", "Müll rausbringen", false, listOf("David", "Mia"), today.plusDays(1).atTime(18, 0)),
            Task("4", "Elternabend", false, listOf("Anna"), today.minusDays(1).atTime(19, 30)),
        ),
        members = listOf(Member("a", "David", 0xE8C77A), Member("b", "Anna", 0xFF8A65), Member("c", "Mia", 0x82B1FF)),
    )

    private fun shot(name: String, content: @Composable () -> Unit) =
        captureRoboImage("build/screens/$name.png") { FamilyListTheme { Backdrop(content) } }

    @Test fun shop() = shot("shop") { ShopTab(data, "David") }
    @Test fun shopEmpty() = shot("shop-empty") { ShopTab(FamilyData(), "") }
    @Test fun tasks() = shot("tasks") { TasksTab(data, "David") }
    @Test fun newTask() = shot("task-new") { TaskEditor(null, data, "David", {}, {}) }
    @Test fun editTask() = shot("task-edit") { TaskEditor(data.tasks[2], data, "David", {}, {}) }

    @Test fun settings() {
        Store.init(ApplicationProvider.getApplicationContext())
        shot("settings") { SettingsScreen {} }
    }
}
