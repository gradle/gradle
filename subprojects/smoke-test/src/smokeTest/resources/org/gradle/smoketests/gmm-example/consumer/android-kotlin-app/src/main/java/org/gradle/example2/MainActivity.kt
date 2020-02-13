package org.gradle.example2

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

import example.androidkotlinlib.AndroidKotlinLibraryUtil
import example.androidlibsingle.AndroidLibrarySingleVariantUtil
import example.androidlib.AndroidLibraryUtil
import example.javalib.JavaLibraryUtil
import example.kotlinlib.KotlinLibraryUtil
import example.kotlinlibmp.KotlinMultiplatformLibraryUtil
import example.kotlinlibmpandroid.KotlinMultiplatformAndroidLibraryUtil

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(getApplicationContext())

        val info = """
            ${JavaLibraryUtil.use()}
            ${KotlinLibraryUtil.use()}
            ${AndroidLibraryUtil.use()}
            ${AndroidLibrarySingleVariantUtil.use()}
            ${AndroidKotlinLibraryUtil.use()}
            ${KotlinMultiplatformLibraryUtil.use()}
            ${KotlinMultiplatformAndroidLibraryUtil.use()}
        """.trimIndent()

        textView.text = info
        setContentView(textView)
    }
}
