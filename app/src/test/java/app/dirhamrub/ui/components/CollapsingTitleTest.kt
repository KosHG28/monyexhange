package app.dirhamrub.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollapsingTitleTest {
    @Test fun `a programmatic scroll settles even when it changes the scroll-in-progress state`() = runTest {
        val scroll = ScrollState(32)
        val clock = BroadcastFrameClock()
        var attempts = 0
        backgroundScope.launch(StandardTestDispatcher(testScheduler) + clock) {
            settleTitleWhenIdle({ scroll.isScrollInProgress }) {
                if (scroll.value in 1 until 56) {
                    attempts++
                    scroll.animateScrollTo(56, tween(240))
                }
            }
        }
        runCurrent()
        repeat(20) { frame ->
            clock.sendFrame(frame * 16_666_667L)
            Snapshot.sendApplyNotifications()
            runCurrent()
        }
        assertEquals(56, scroll.value)
        assertEquals(1, attempts)
    }
}
