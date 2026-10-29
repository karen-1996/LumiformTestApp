package com.lumiformtest.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplashActivityTest {
    @Test
    fun launcherHandsOverToMainWithoutKeepingSplashInTheBackStack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val launchIntent = requireNotNull(
            context.packageManager.getLaunchIntentForPackage(context.packageName)
        )
        assertEquals(SplashActivity::class.java.name, launchIntent.component?.className)

        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        var mainActivity: MainActivity? = null
        try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(launchIntent)
            mainActivity = instrumentation.waitForMonitorWithTimeout(monitor, 5_000) as? MainActivity
            assertNotNull("The launcher should open MainActivity", mainActivity)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue("Back should not return to the splash", mainActivity!!.isTaskRoot)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { mainActivity?.finishAndRemoveTask() }
        }
    }
}
