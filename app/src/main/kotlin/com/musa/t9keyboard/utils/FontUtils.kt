package com.musa.t9keyboard.utils

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.musa.t9keyboard.R

object FontUtils {
    private var ubuntu: Typeface? = null

    fun getUbuntu(context: Context): Typeface {
        if (ubuntu == null) {
            ubuntu = ResourcesCompat.getFont(context, R.font.ubuntu_sans_mono)
        }
        return ubuntu!!
    }
}
