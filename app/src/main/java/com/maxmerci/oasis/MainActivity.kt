package com.maxmerci.oasis

import androidx.recyclerview.widget.RecyclerView
import java.lang.reflect.Field
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.maxmerci.oasis.repository.ProfileRepository
import com.maxmerci.oasis.serialization.storage.ProfileData
import com.maxmerci.oasis.ui.chat.ChatFragment
import com.maxmerci.oasis.ui.data.DataFragment
import com.maxmerci.oasis.ui.models.ModelsFragment
import com.tbuonomo.viewpagerdotsindicator.WormDotsIndicator

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Oasis.init(this)

        if (ProfileRepository.profiles.isEmpty())
            ProfileRepository.add(
                ProfileData(
                    "ыфвфывфыв",
                    "https://api.laozhang.ai/v1"
                )
            )

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        setupViewPager()
    }

    private fun setupViewPager() {
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val dotsIndicator = findViewById<WormDotsIndicator>(R.id.dots_indicator)

        viewPager.adapter = MainPagerAdapter(this)
        viewPager.setCurrentItem(1, false)

        // чувствительность
        viewPager.reduceDragSensitivity(4)

        dotsIndicator.attachTo(viewPager)
    }

    private class MainPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> DataFragment()
                1 -> ChatFragment()
                2 -> ModelsFragment()
                else -> throw IllegalArgumentException("Invalid position")
            }
        }
    }

    fun ViewPager2.reduceDragSensitivity(f: Int = 4) {
        try {
            val recyclerViewField: Field = ViewPager2::class.java.getDeclaredField("mRecyclerView")
            recyclerViewField.isAccessible = true
            val recyclerView = recyclerViewField.get(this) as RecyclerView

            val touchSlopField: Field = RecyclerView::class.java.getDeclaredField("mTouchSlop")
            touchSlopField.isAccessible = true
            val touchSlop = touchSlopField.get(recyclerView) as Int
            touchSlopField.set(recyclerView, touchSlop * f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}