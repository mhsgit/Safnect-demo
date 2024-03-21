package com.populstay.wallet.device

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.populstay.wallet.CommonUtil
import com.populstay.wallet.R
import com.populstay.wallet.base.BaseActivity
import com.populstay.wallet.databinding.ActivityPairingSuccessfulBinding
import com.populstay.wallet.eventbus.Event
import com.populstay.wallet.home.view.HomeActivity
import org.greenrobot.eventbus.EventBus

class PairingSuccessfulActivity : BaseActivity() {

    companion object{
        const val FORM_TYPE = "form_type"
        const val FORM_TYPE_DKG = 0
        const val FORM_TYPE_TRANS = 1
        const val SENT_HASH = "sent_hash"
    }

   private lateinit var binding : ActivityPairingSuccessfulBinding
   private var formType = FORM_TYPE_DKG
    private var sentHash : String ? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingSuccessfulBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initIntentData()
        initTitleBar()
    }

    private fun initIntentData() {
        formType = intent.getIntExtra(FORM_TYPE,FORM_TYPE_DKG)
        sentHash = intent.getStringExtra(SENT_HASH)
    }

    override fun initTitleBar() {

        when(formType){
            FORM_TYPE_DKG ->{
                binding.commonTitleLayout.back.setOnClickListener {
                    gotoToWallet()
                }
                binding.commonTitleLayout.titleTv.setText(R.string.pairing_successful)
                binding.commonTitleLayout.titleTv.visibility = View.GONE
                binding.gotoToWallet.setOnClickListener {
                    gotoToWallet()
                }
                binding.gotoToTransRecord.visibility = View.GONE
                binding.successDesc2.visibility = View.INVISIBLE
            }
            FORM_TYPE_TRANS ->{
                binding.commonTitleLayout.back.setOnClickListener {
                    backToWallet()
                }
                binding.commonTitleLayout.titleTv.setText(R.string.transfer_successfully)
                binding.commonTitleLayout.titleTv.visibility = View.GONE
                binding.gotoToWallet.setOnClickListener {
                    backToWallet()
                }
                binding.successDesc.visibility = View.GONE
                binding.gotoToTransRecord.visibility = View.VISIBLE
                binding.gotoToTransRecord.setOnClickListener {
                    backToWallet(Event.EventType.BACK_TRANSFER)
                }
                binding.successDesc2.visibility = View.VISIBLE
                binding.successDesc2.setOnClickListener {
                    CommonUtil.clipboardText(this@PairingSuccessfulActivity,binding.successDesc2.text.toString())
                    Toast.makeText(this@PairingSuccessfulActivity,resources.getString(R.string.copied_hash_hint), Toast.LENGTH_SHORT).show()
                }
                sentHash?.let {
                    binding.successDesc2.text = "sentHash:$it"
                }
            }
        }


    }

    private fun gotoToWallet(){
        val intent = Intent(this@PairingSuccessfulActivity, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun backToWallet(type : Int = Event.EventType.BACK_WALLET){
        EventBus.getDefault().post(Event(type))
        finish()
    }


}