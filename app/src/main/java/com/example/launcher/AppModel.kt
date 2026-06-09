package com.example.launcher

import android.content.Context
import android.content.Intent

data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val customLabel: String,
    val isHidden: Boolean,
    val isFavorite: Boolean
) {
    val displayName: String
        get() = customLabel.ifEmpty { label }

    fun launch(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                val leafIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setClassName(packageName, activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(leafIntent)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
