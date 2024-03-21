package com.populstay.wallet.welcome

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.TextView
import com.populstay.wallet.BuildConfig
import com.populstay.wallet.FileUitl
import com.populstay.wallet.R
import com.populstay.wallet.SharedPreferencesUtil
import com.populstay.wallet.databinding.ActivityTestBinding
import com.populstay.wallet.databinding.ActivityWelcomeBinding
import com.populstay.wallet.home.view.HomeActivity
import com.populstay.wallet.log.PeachLogger
import com.populstay.wallet.mpc.IMpc
import com.populstay.wallet.mpc.ImplMpc
import com.populstay.wallet.storagetype.SelectStorageTypeActivity

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    private val mpc by lazy {
        ImplMpc()
    }

    companion object{
        const val OLD_ACCOUNT = "old_account"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PeachLogger.d("WelcomeActivity", "Version:${BuildConfig.VERSION_NAME}")

        //设置字体白色
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

        if (SharedPreferencesUtil.getBoolean(this,OLD_ACCOUNT,false)){
            Handler().postDelayed({
                if (mpc.accountCreated(IMpc.ROLE_CLIENT, FileUitl.getConfigDir())){
                    // 已经创建好账号
                    toMain()
                }else{
                    toSelectStorageTypeActivity()
                }
                finish()
            },800)
        }

        binding.getStarted.setOnClickListener {
            SharedPreferencesUtil.saveBoolean(this,OLD_ACCOUNT,true)
            if (mpc.accountCreated(IMpc.ROLE_CLIENT, FileUitl.getConfigDir())){
                // 已经创建好账号
                toMain()
            }else{
                toSelectStorageTypeActivity()
            }
            finish()
        }

    }

    private fun toMain(){
        val intent = Intent(this@WelcomeActivity, HomeActivity::class.java)
        startActivity(intent)
    }

    private fun toSelectStorageTypeActivity(){
        val intent = Intent(this@WelcomeActivity, SelectStorageTypeActivity::class.java)
        startActivity(intent)
    }

}