package org.gradle.smoketest.kotlin.android

import android.app.Fragment
import android.util.Log

class StringPrinterFragment : Fragment() {

    fun printStringLength(str: String) = Log.d("StringPrinter", str.length.toString())
}
