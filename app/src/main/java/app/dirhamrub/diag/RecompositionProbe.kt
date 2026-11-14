package app.dirhamrub.diag

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import app.dirhamrub.BuildConfig

/** Opt-in debug measurement only; R8 removes the probe and its calls in release. */
internal object RecompositionTrace {
    var enabled = false
    private val counts = HashMap<String, Int>()
    fun configure(enabled: Boolean) {
        this.enabled = enabled
        counts.clear()
    }
    fun record(name: String) {
        val count = (counts[name] ?: 0) + 1
        counts[name] = count
        Log.i("KpcCompose", "$name=$count")
    }
}

@Composable
@NonRestartableComposable
internal fun RecompositionProbe(name: String) {
    if (BuildConfig.DEBUG && RecompositionTrace.enabled) SideEffect { RecompositionTrace.record(name) }
}
