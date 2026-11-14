package app.dirhamrub.diag

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Read-only observations independent of Android's trim-level state machine. Debug source set only. */
class MemoryAuditReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LifetimeAudit.report()
    }
}
