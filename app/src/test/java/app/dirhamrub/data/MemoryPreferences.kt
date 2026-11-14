package app.dirhamrub.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class MemoryPreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    val values = MutableStateFlow(initial)
    val readThreads = mutableListOf<Thread>()
    var beforeUpdate: suspend () -> Unit = {}
    private val mutex = Mutex()
    override val data: Flow<Preferences> = flow {
        readThreads += Thread.currentThread()
        values.collect { emit(it) }
    }
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            beforeUpdate()
            transform(values.value).also { values.value = it }
        }
}
