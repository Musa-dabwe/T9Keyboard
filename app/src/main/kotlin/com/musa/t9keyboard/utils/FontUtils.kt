package com.musa.t9keyboard.utils

import android.content.Context
import android.graphics.Typeface

object FontUtils {
    private var ubuntu: Typeface? = null

    fun getUbuntu(context: Context): Typeface {
        if (ubuntu == null) {
            ubuntu = Typeface.createFromAsset(context.assets, "ubuntu.ttf")
        }
        return ubuntu!!
    }
}
