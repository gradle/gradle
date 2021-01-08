package org.gradle.smoketest.kotlin.android

import android.app.Fragment
import android.util.Log
import org.jetbrains.anko.runOnUiThread

class StringPrinterFragment : Fragment() {

    fun printStringLength(str: String) = runOnUiThread {
        Log.d("StringPrinter", str.length.toString())
    }
}
