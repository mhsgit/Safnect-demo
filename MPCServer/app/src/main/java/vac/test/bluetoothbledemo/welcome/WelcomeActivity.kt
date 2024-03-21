package vac.test.bluetoothbledemo.welcome

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.View
import vac.test.bluetoothbledemo.R
import vac.test.bluetoothbledemo.ui.MainActivity


class WelcomeActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        //设置字体白色
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

        Handler().postDelayed({
            toMain()
            finish()
        },1000)

    }

    private fun toMain(){
        val intent = Intent(this@WelcomeActivity, MainActivity::class.java)
        startActivity(intent)
    }

}