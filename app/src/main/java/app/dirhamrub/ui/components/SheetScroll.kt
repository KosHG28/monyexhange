package app.dirhamrub.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity

/**
 * Прокрутка внутри шторки ([fadingScroll]), которая не закрывает шторку случайно: шторку тянет вниз только жест,
 * начатый, когда список уже в самом начале. Иначе жест (и его инерция) лишь долистывает список до верха —
 * остаток не уходит шторке, и она не уезжает посреди чтения.
 */
fun Modifier.sheetScroll(state: ScrollState): Modifier = composed {
    val guard = remember(state) { SheetScrollGuard() }
    this
        .pointerInput(state) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                guard.startedAtTop = state.value == 0
            }
        }
        .nestedScroll(guard)
        .fadingScroll(state)
}

private class SheetScrollGuard : NestedScrollConnection {
    var startedAtTop = true

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (startedAtTop && source == NestedScrollSource.UserInput) Offset.Zero else Offset(0f, available.y)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (startedAtTop) Velocity.Zero else Velocity(0f, available.y)
}
