package org.gradle.example;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import example.androidkotlinlib.AndroidKotlinLibraryUtil;
import example.androidlibsingle.AndroidLibrarySingleVariantUtil;
import example.androidlib.AndroidLibraryUtil;
import example.javalib.JavaLibraryUtil;
import example.kotlinlib.KotlinLibraryUtil;
import example.kotlinlibmp.KotlinMultiplatformLibraryUtil;
import example.kotlinlibmpandroid.KotlinMultiplatformAndroidLibraryUtil;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView textView = new TextView(getApplicationContext());

        String info = "";
        info += JavaLibraryUtil.use() + "\n";
        info += KotlinLibraryUtil.INSTANCE.use() + "\n";
        info += AndroidLibraryUtil.use() + "\n";
        info += AndroidLibrarySingleVariantUtil.use() + "\n";
        info += AndroidKotlinLibraryUtil.INSTANCE.use() + "\n";
        info += KotlinMultiplatformLibraryUtil.INSTANCE.use() + "\n";
        info += KotlinMultiplatformAndroidLibraryUtil.INSTANCE.use() + "\n";

        textView.setText(info);
        setContentView(textView);
    }
}
