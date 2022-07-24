package myapp

import example.androidkotlinlib.AndroidKotlinLibraryUtil
import example.androidlibsingle.AndroidLibrarySingleVariantUtil
import example.androidlib.AndroidLibraryUtil
import example.javalib.JavaLibraryUtil
import example.kotlinlib.KotlinLibraryUtil
import example.kotlinlibmp.KotlinMultiplatformLibraryUtil
import example.kotlinlibmpandroid.KotlinMultiplatformAndroidLibraryUtil

fun main() {
    JavaLibraryUtil.use()
    KotlinLibraryUtil.use()
    AndroidLibraryUtil.use()
    AndroidLibrarySingleVariantUtil.use()
    AndroidKotlinLibraryUtil.use()
    KotlinMultiplatformLibraryUtil.use()
    KotlinMultiplatformAndroidLibraryUtil.use()
}
