package vac.test.bluetoothbledemo

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vac.test.bluetoothbledemo.repository.BlueToothBLEUtil

class BaseApp : BApp() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var mContext: Context

        @SuppressLint("StaticFieldLeak")
        lateinit var instance: BaseApp
    }

    override fun onCreate() {
        super.onCreate()

        // 注册 AppLifecycleObserver
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver(applicationScope))

        instance = this
        mContext = this

        applicationScope.launch {
            withContext(Dispatchers.IO) {
                // 在 IO 线程执行耗时操作
                FileUitl.copyAssetSubdirectoriesToFolder(mContext)
            }
        }

        //初始化BlueToothBLEUtil
        BlueToothBLEUtil.init(this)
    }
}