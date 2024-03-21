package vac.test.bluetoothbledemo

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object SingleThreadExecutor {

    private val executor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    fun execute(task: () -> Unit) {
       // executor.execute { task.invoke() }

        if (!executor.isShutdown) {
            executor.execute { task.invoke() }
        }
    }

    fun shutdown() {
        executor.shutdown()
    }
}
