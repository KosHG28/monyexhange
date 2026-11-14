package app.dirhamrub.ui.camera

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Accept late ML Kit completions forever, but release the worker's stack after camera idle. */
internal fun newAnalysisExecutor(idleMillis: Long = 15_000): ThreadPoolExecutor = ThreadPoolExecutor(
    0, 1, idleMillis, TimeUnit.MILLISECONDS, LinkedBlockingQueue(),
    { task -> Thread(task, "price-analysis").apply { isDaemon = true } },
)
