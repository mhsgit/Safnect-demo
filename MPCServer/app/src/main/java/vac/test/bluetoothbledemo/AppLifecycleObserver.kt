package vac.test.bluetoothbledemo

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

class AppLifecycleObserver(private val scope: CoroutineScope) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        // 如果应用进入后台，取消所有正在执行的携程
        scope.cancel()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onAppTerminated() {
        // 如果应用被销毁，取消所有正在执行的携程
        scope.cancel()
    }
}