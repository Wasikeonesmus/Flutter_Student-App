package com.examsystem.app.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Resolves the [Activity] from Compose [LocalContext] (often a [ContextWrapper]). */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? Activity
}
