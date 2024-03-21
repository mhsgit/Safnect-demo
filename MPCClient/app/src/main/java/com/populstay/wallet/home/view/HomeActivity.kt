package com.populstay.wallet.home.view

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.populstay.wallet.R
import com.populstay.wallet.base.BaseActivity
import com.populstay.wallet.databinding.ActivityHomeBinding
import com.populstay.wallet.eventbus.Event
import com.populstay.wallet.log.PeachLogger
import com.populstay.wallet.ui.BaseFragment
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class HomeActivity : BaseActivity() {

    lateinit var binding : ActivityHomeBinding

    private lateinit var mWalletFragment: WalletFragment
    private lateinit var mTransactionFragment: TransactionFragment
    private lateinit var mMeFragment: MeFragment
    private lateinit var fragments: ArrayList<BaseFragment<out ViewBinding>>
    private lateinit var viewpagerAdapter: FragmentStateAdapter

    private val tabIcons by lazy {
        listOf(R.drawable.tab_icon_wallet,R.drawable.tab_icon_transaction,R.drawable.tab_icon_me)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
        initTitleBar()
        initTabAndViewPager()
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    private fun initTabAndViewPager() {
        mWalletFragment = WalletFragment.newInstance()
        mTransactionFragment = TransactionFragment.newInstance()
        mMeFragment = MeFragment.newInstance()
        fragments = arrayListOf(mWalletFragment,mTransactionFragment,mMeFragment)

        viewpagerAdapter =  object :
            FragmentStateAdapter(supportFragmentManager, this.lifecycle) {
            override fun getItemCount(): Int {
                return fragments.size
            }

            override fun createFragment(position: Int): Fragment {
                return fragments[position]
            }

        }
        binding.viewPager.adapter = viewpagerAdapter
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.offscreenPageLimit = 2

        val tabLayoutMediator =
            TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
                tab.customView = getViewAtI(position)
            }
        tabLayoutMediator.attach()


        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener{
            override fun onTabSelected(tab: TabLayout.Tab?) {

            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {

            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }

        })
    }

    private fun getViewAtI(position: Int): View? {
        val view: View = layoutInflater.inflate(R.layout.tab_item_view, null, false)
        val imageView: ImageView = view.findViewById(R.id.icon)
        imageView.setImageResource(tabIcons[position])
        return view
    }

    override fun initTitleBar() {

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: Event?) {
        if (null == event || event.type <= 0) {
            PeachLogger.d("onEvent--无效事件")
            return
        }

        when(event.type){
            Event.EventType.BACK_TRANSFER ->{
                binding.viewPager.setCurrentItem(1,true)
            }
        }
    }
}