package app.dirhamrub.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

/**
 * Пружины Material 3 Expressive вместо тайминга по кривой: движение «живое», с лёгким перелётом в конце.
 * Пространственные (сдвиг, размер) — с перелётом; эффекты (прозрачность, цвет) — без него, быстрее.
 * Уход с экрана — без перелёта, чтобы ничего не «подпрыгивало» на выходе.
 */
object Springs {
    /** Сдвиг и размер: экран въезжает, карта летит. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Мелкие элементы: клавиши, кнопки — быстрее и пружинистее. */
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /** Уход: без перелёта. */
    fun <T> exit(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1000f)

    /** Прозрачность и цвет. */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)

    /** Экран поверх главного: въезжает снизу на четверть высоты с лёгким перелётом. */
    val screenIn: EnterTransition get() = slideInVertically(spatial()) { it / 4 } + fadeIn(effects())
    val screenOut: ExitTransition get() = slideOutVertically(exit()) { it / 4 } + fadeOut(fastEffects())

    /** Настройки — справа. */
    val sideIn: EnterTransition get() = slideInHorizontally(spatial()) { it / 3 } + fadeIn(effects())
    val sideOut: ExitTransition get() = slideOutHorizontally(exit()) { it / 3 } + fadeOut(fastEffects())
}
