package com.populstay.wallet.ui.widget

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.widget.NestedScrollView


class BounceNestedScrollView : NestedScrollView {
    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet?) : super(
        context, attrs
    ) {
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    ) {
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int
    ) {
        if (dyUnconsumed < 0 && scrollY <= 0) {
            // 执行下拉回弹效果
            val animator = ObjectAnimator.ofInt(this, "scrollY", scrollY, 0)
            animator.duration = 300
            animator.start()
        } else if (dyUnconsumed > 0 && getChildAt(0).height - height <= scrollY) {
            // 执行上拉回弹效果
            val animator =
                ObjectAnimator.ofInt(this, "scrollY", scrollY, getChildAt(0).height - height)
            animator.duration = 300
            animator.start()
        }
        super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed)
    }

    companion object {
        private const val MAX_OVER_SCROLL_Y = 200 // 设置最大的超过滚动距离，可根据需求调整
    }
}
