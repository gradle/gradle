package myapp;

import example.androidkotlinlib.AndroidKotlinLibraryUtil;
import example.androidlibsingle.AndroidLibrarySingleVariantUtil;
import example.androidlib.AndroidLibraryUtil;
import example.javalib.JavaLibraryUtil;
import example.kotlinlib.KotlinLibraryUtil;
import example.kotlinlibmp.KotlinMultiplatformLibraryUtil;
import example.kotlinlibmpandroid.KotlinMultiplatformAndroidLibraryUtil;

public class App {

    public static void main(String[] args) {
        JavaLibraryUtil.use();
        KotlinLibraryUtil.INSTANCE.use();
        AndroidLibraryUtil.use();
        AndroidLibrarySingleVariantUtil.use();
        AndroidKotlinLibraryUtil.INSTANCE.use();
        KotlinMultiplatformLibraryUtil.INSTANCE.use();
        KotlinMultiplatformAndroidLibraryUtil.INSTANCE.use();
    }

}
