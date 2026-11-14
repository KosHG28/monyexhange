package app.dirhamrub.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

private val LocalFrameRate = staticCompositionLocalOf<FrameRateController?> { null }

/** One owner per real Window (Activity or full-screen Dialog), shared by all its animations. */
@Composable
fun AdaptiveFrameRate(content: @Composable () -> Unit) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: view.context.activity()?.window
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduce = LocalReduceMotion.current
    val controller = remember(view, window) { FrameRateController(view, window) }
    val scroll = remember(controller) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed != Offset.Zero || available != Offset.Zero) controller.interaction()
                return Offset.Zero
            }
        }
    }
    SideEffect { controller.reduced(reduce) }
    DisposableEffect(controller, lifecycle) {
        controller.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, _ ->
            controller.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        val attachment = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                controller.invalidate()
                controller.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            }
            override fun onViewDetachedFromWindow(v: View) { controller.foreground(false) }
        }
        view.addOnAttachStateChangeListener(attachment)
        lifecycle.addObserver(observer)
        onDispose {
            view.removeOnAttachStateChangeListener(attachment)
            lifecycle.removeObserver(observer)
            controller.foreground(false)
            controller.restore()
        }
    }
    LaunchedEffect(controller) {
        try { controller.observe() } finally { controller.restore() }
    }
    CompositionLocalProvider(LocalFrameRate provides controller) {
        Box(Modifier.fillMaxSize().nestedScroll(scroll).pointerInput(controller) {
            // Observe only; taps, long presses, multi-touch and scrolling keep their original handlers.
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    controller.interaction()
                }
            }
        }) { content() }
    }
}

/** Independent animation leases cannot cancel each other's high refresh request. */
@Composable
fun HighFrameRate(active: Boolean) {
    val controller = LocalFrameRate.current
    val owner = remember { Any() }
    DisposableEffect(controller, owner, active) {
        controller?.animation(owner, active)
        onDispose { controller?.animation(owner, false) }
    }
}

private class FrameRateController(private val view: View, private val window: Window?) {
    private val policy = FrameRatePolicy()
    private val events = Channel<Unit>(Channel.CONFLATED)
    private var applied: Boolean? = null
    private val originalWindowRate = window?.attributes?.preferredRefreshRate ?: 0f
    private val originalViewRate = if (Build.VERSION.SDK_INT >= 35) view.requestedFrameRate else 0f
    private fun highRate(): Float = view.display?.let { display ->
        val current = display.mode
        display.supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxOfOrNull { it.refreshRate }
    } ?: 0f

    fun invalidate() { applied = null; events.trySend(Unit) }
    fun foreground(value: Boolean) { policy.foreground(value); events.trySend(Unit) }
    fun reduced(value: Boolean) { policy.reduced(value); events.trySend(Unit) }
    fun animation(owner: Any, active: Boolean) { policy.animation(owner, active); events.trySend(Unit) }
    fun interaction() { policy.interaction(SystemClock.uptimeMillis()); events.trySend(Unit) }

    suspend fun observe() {
        while (true) {
            val now = SystemClock.uptimeMillis()
            apply(policy.boost(now))
            val expiry = policy.expiryDelay(now)
            if (expiry != null) withTimeoutOrNull(expiry) { events.receive() } else events.receive()
        }
    }

    private fun apply(high: Boolean) {
        if (applied == high) return
        applied = high
        if (Build.VERSION.SDK_INT >= 35) {
            // NO_PREFERENCE relinquishes our vote, allowing the system/panel to choose its idle rate.
            category(view, if (high) View.REQUESTED_FRAME_RATE_CATEGORY_HIGH else View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE)
        } else {
            // Legacy devices: a temporary preference at the current resolution; zero restores system choice.
            window?.let { w ->
                val attributes = w.attributes
                attributes.preferredRefreshRate = if (high) highRate() else 0f
                w.attributes = attributes
            }
        }
    }

    fun restore() {
        if (Build.VERSION.SDK_INT >= 35) category(view, originalViewRate)
        else window?.let { w ->
            val attributes = w.attributes
            attributes.preferredRefreshRate = originalWindowRate
            w.attributes = attributes
        }
        applied = null
    }
}

@RequiresApi(35)
private fun category(view: View, rate: Float) { view.requestedFrameRate = rate }

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.activity()
    else -> null
}
