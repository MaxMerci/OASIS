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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import mm.oasis.remote.UpdateManager
import mm.oasis.ui.modal.DialogField
import mm.oasis.ui.modal.FieldType
import mm.oasis.ui.modal.ModalDialogBuilder

class MainActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private val updateManager by lazy { UpdateManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        Oasis.init(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        setupViewPager()
        setupBackPressedHandling()
        checkForUpdates()
    }

    private fun checkForUpdates() {
        val currentVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: ""

        lifecycleScope.launch {
            val release = updateManager.checkForUpdates(currentVersion)
            if (release != null) {
                ModalDialogBuilder(this@MainActivity)
                    .setTitle("UPDATE FOUND")
                    .addField(DialogField("", "[${release.tagName}]: Open release page?", FieldType.HEADER, false))
                    .onOk {
                        updateManager.openUpdateLink(release.htmlUrl)
                    }
                    .show()
            }
        }
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
