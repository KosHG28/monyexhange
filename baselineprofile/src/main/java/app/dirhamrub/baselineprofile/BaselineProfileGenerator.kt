package app.dirhamrub.baselineprofile

import java.io.ByteArrayOutputStream
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real UI, with no demo seed, camera, screenshots or country-information screen. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun prepareWallet() {
        device.executeShellCommand("am start -W -n $PACKAGE/app.dirhamrub.MainActivity")
        await {
            if (device.hasObject(By.desc("9"))) true else {
                device.findObject(By.text("Пропустить"))?.click()
                false
            }
        }
        if (!device.hasObject(By.descContains(FIRST))) addCard(FIRST)
        if (!device.hasObject(By.descContains(SECOND))) addCard(SECOND)
        device.waitForIdle()
        Thread.sleep(500) // Let the normal debounced session save complete before collection kills the process.
        device.pressHome()
    }

    @Test fun startup() = rule.collect(
        packageName = PACKAGE, includeInStartupProfile = true,
        maxIterations = 5, stableIterations = 3,
    ) {
        pressHome()
        startActivityAndWait()
        check(device.wait(Until.hasObject(By.desc("9")), 10_000))
    }

    @Test fun calculatorAndEditor() = rule.collect(
        packageName = PACKAGE, includeInStartupProfile = false,
        maxIterations = 5, stableIterations = 3,
    ) {
        pressHome()
        startActivityAndWait()
        await { device.hasObject(By.desc("9")) }
        for (digit in listOf("7", "8", "9", "4", "5", "6")) click { device.findObject(By.desc(digit)) }
        var name = SECOND
        await {
            val row = device.findObject(By.descContains(SECOND)) ?: return@await false
            // Compose's RadioButton role maps selected semantics to accessibility checked on API 34.
            name = if (row.isSelected || row.isChecked) FIRST else SECOND
            true
        }
        click { device.findObject(By.descContains(name)) }
        await {
            val row = device.findObject(By.descContains(name)) ?: return@await false
            row.isSelected || row.isChecked
        }
        device.waitForIdle()
        click { device.findObject(By.descContains(name)) }
        node { device.findObject(By.desc("Назад")) }
        device.waitForIdle()
        device.pressBack()
        await { device.hasObject(By.desc("9")) }
    }

    private fun addCard(name: String) {
        click { device.findObject(By.textContains("Добавить карту")) }
        val field = node { device.findObject(By.clazz("android.widget.EditText")) }
        field.click()
        device.waitForIdle()
        field.text = name
        device.pressBack() // Hide IME; save stays a normal UI action.
        click { device.findObject(By.text("Сохранить")) }
        await { device.hasObject(By.desc("9")) }
        node { device.findObject(By.descContains(name)) }
    }

    private fun click(find: () -> UiObject2?) {
        await {
            val target = find() ?: return@await false
            target.click()
            true
        }
    }

    private fun node(find: () -> UiObject2?): UiObject2 {
        var result: UiObject2? = null
        await { result = find(); result != null }
        return checkNotNull(result)
    }

    private fun await(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < end) {
            try {
                if (condition()) return
            } catch (_: StaleObjectException) {
                // Compose can replace accessibility nodes while a selected row animates.
                // Re-query on the next attempt instead of retaining a detached UiObject2.
            }
            Thread.sleep(100)
        }
        val xml = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString("UTF-8")
        error("Expected calculator UI did not appear. Accessibility hierarchy: $xml")
    }

    companion object {
        const val PACKAGE = "ru.kurspocheku"
        const val FIRST = "Профиль первая"
        const val SECOND = "Профиль вторая"
    }
}
