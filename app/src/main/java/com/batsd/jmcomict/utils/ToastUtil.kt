package com.batsd.jmcomict.utils

import android.content.Context
import android.widget.Toast

/**
 * Toast 工具类
 */
object ToastUtil {
    fun showShort(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    
    fun showLong(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
