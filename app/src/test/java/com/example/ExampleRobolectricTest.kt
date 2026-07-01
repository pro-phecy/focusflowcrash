package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.Converters
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FocusFlow", appName)
  }

  @Test
  fun testConvertersListSerialization() {
    val converters = Converters()
    val originalList = listOf("chrome", "youtube", "twitter")
    val jsonString = converters.fromStringList(originalList)
    val parsedList = converters.toStringList(jsonString)
    assertEquals(originalList, parsedList)
  }

  @Test
  fun testActivityResolverBehavior() {
    // Verify context wrapping and Activity finding
    val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).create().get()
    val contextWrapper = android.content.ContextWrapper(activity)
    
    // Mimic the private findActivity function's logic
    var current: Context = contextWrapper
    var resolvedActivity: android.app.Activity? = null
    while (current is android.content.ContextWrapper) {
        if (current is android.app.Activity) {
            resolvedActivity = current
            break
        }
        current = current.baseContext
    }
    
    assertEquals(activity, resolvedActivity)
  }
}
