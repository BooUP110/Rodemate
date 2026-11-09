package io.booup.roadmate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job

/** File IO may resume on Main after cancellation. Await it before resetMain / TemporaryFolder cleanup. */
internal suspend fun clearAndAwaitViewModel(store: ViewModelStore, model: ViewModel) {
    val job = model.viewModelScope.coroutineContext[Job]
    store.clear()
    job?.join()
}
