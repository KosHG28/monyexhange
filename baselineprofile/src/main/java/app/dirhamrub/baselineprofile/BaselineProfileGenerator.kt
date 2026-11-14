package app.dirhamrub.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
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
        for (digit in listOf("7", "8", "9", "4", "5", "6")) node { device.findObject(By.desc(digit)) }.click()
        val first = node { device.findObject(By.descContains(FIRST)) }
        val second = node { device.findObject(By.descContains(SECOND)) }
        val target = if (second.isSelected) first else second
        val name = if (second.isSelected) FIRST else SECOND
        target.click()
        device.waitForIdle()
        node { device.findObject(By.descContains(name)) }.click()
        node { device.findObject(By.desc("Назад")) }
        device.waitForIdle()
        device.pressBack()
        await { device.hasObject(By.desc("9")) }
    }

    private fun addCard(name: String) {
        node { device.findObject(By.textContains("Добавить карту")) }.click()
        val field = node { device.findObject(By.clazz("android.widget.EditText")) }
        field.click()
        device.waitForIdle()
        field.text = name
        device.pressBack() // Hide IME; save stays a normal UI action.
        node { device.findObject(By.text("Сохранить")) }.click()
        await { device.hasObject(By.desc("9")) }
        node { device.findObject(By.descContains(name)) }
    }

    private fun node(find: () -> UiObject2?): UiObject2 {
        var result: UiObject2? = null
        await { result = find(); result != null }
        return checkNotNull(result)
    }

    private fun await(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(100)
        }
        error("Expected calculator UI did not appear")
    }

    companion object {
        const val PACKAGE = "ru.kurspocheku"
        const val FIRST = "Профиль первая"
        const val SECOND = "Профиль вторая"
    }
}
