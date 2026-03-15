package mm.oasis

import androidx.recyclerview.widget.RecyclerView
import java.lang.reflect.Field
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import mm.oasis.ui.chat.ChatFragment
import mm.oasis.ui.data.DataFragment
import mm.oasis.ui.models.ModelsFragment
import com.tbuonomo.viewpagerdotsindicator.WormDotsIndicator

class MainActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        Oasis.init(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        setupViewPager()
        setupBackPressedHandling()
    }

    private fun setupViewPager() {
        viewPager = findViewById(R.id.viewPager)
        val dotsIndicator = findViewById<WormDotsIndicator>(R.id.dots_indicator)

        viewPager.adapter = MainPagerAdapter(this)
        viewPager.setCurrentItem(1, false)

        // чувствительность
        viewPager.reduceDragSensitivity(5)

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

    private fun setupBackPressedHandling() {
        val callback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewPager.currentItem != 1) {
                    viewPager.currentItem = 1
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }
}