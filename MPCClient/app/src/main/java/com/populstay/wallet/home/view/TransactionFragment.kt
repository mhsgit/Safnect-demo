package com.populstay.wallet.home.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.populstay.wallet.R
import com.populstay.wallet.databinding.FragmentTransactionBinding
import com.populstay.wallet.eventbus.Event
import com.populstay.wallet.log.PeachLogger
import com.populstay.wallet.transaction.view.TransContentFragment
import com.populstay.wallet.ui.BaseFragment
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class TransactionFragment : BaseFragment<FragmentTransactionBinding>() {


    companion object {
        fun newInstance() = TransactionFragment()
    }

    private val tabTitles by lazy {
        listOf("All","Transfer","Receive")
    }

    private lateinit var fragments: ArrayList<BaseFragment<out ViewBinding>>
    private lateinit var viewpagerAdapter: FragmentStateAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override val bindingInflater: (LayoutInflater, ViewGroup?, Bundle?) -> FragmentTransactionBinding
        get() = { layoutInflater, viewGroup, _ ->
            FragmentTransactionBinding.inflate(layoutInflater, viewGroup, false)
    }

    override fun initView() {
        super.initView()
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
        initTabAndViewPager()
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
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

    private fun initTabAndViewPager() {
        fragments = arrayListOf(
            TransContentFragment.newInstance(TransContentFragment.TRANS_TYPE_ALL),
            TransContentFragment.newInstance(TransContentFragment.TRANS_TYPE_TRANSFER),
            TransContentFragment.newInstance(TransContentFragment.TRANS_TYPE_RECEIVE)
        )

        viewpagerAdapter =  object :
            FragmentStateAdapter(childFragmentManager, this.lifecycle) {
            override fun getItemCount(): Int {
                return fragments.size
            }

            override fun createFragment(position: Int): Fragment {
                return fragments[position]
            }

        }
        binding.viewPager.adapter = viewpagerAdapter
        //binding.viewPager.offscreenPageLimit = 3

        val tabLayoutMediator =
            TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
                tab.customView = getViewAtI(position)
            }
        tabLayoutMediator.attach()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener{

            override fun onTabSelected(tab: TabLayout.Tab?) {
                //tab?.customView?.findViewById<ImageView>(R.id.icon)?.visibility = View.VISIBLE
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                //tab?.customView?.findViewById<ImageView>(R.id.icon)?.visibility = View.INVISIBLE
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                //tab?.customView?.findViewById<ImageView>(R.id.icon)?.visibility = View.VISIBLE
            }

        })
    }

    private fun getViewAtI(position: Int): View? {
        val view: View = layoutInflater.inflate(R.layout.tab_item_view_trans, null, false)
        val textView: TextView = view.findViewById(R.id.text)
        val imageView: ImageView = view.findViewById(R.id.icon)
        textView.text = tabTitles[position]
        imageView.setImageResource(R.drawable.tab_icon_trans_content)
        return view
    }


}