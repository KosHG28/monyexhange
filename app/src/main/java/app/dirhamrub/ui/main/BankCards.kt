package app.dirhamrub.ui.main

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Размеры «банковской» карточки ([BankCardView]): она осталась в редакторе карты как предпросмотр. */

internal val CARD_RADIUS = 18.dp
internal val CARD_SHAPE = RoundedCornerShape(CARD_RADIUS)

/** Поле вокруг карточки (раньше в нём рисовалась обводка выбранной карты в ряду). */
internal val RING_GAP = 4.dp

/** Место под звезду лучшей карты в строке названия. */
internal val STAR_SLOT = 16.dp

/** Курс на карточке не сокращается многоточием: шрифт уменьшается до этого размера. */
internal val RATE_MIN_SP = 9.sp

/** Уже — компактная карточка: сумма мельче, наценка под курсом. */
internal val COMPACT_CARD_WIDTH = 130.dp
