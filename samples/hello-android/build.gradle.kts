import com.android.builder.core.DefaultApiVersion
import com.android.builder.core.DefaultProductFlavor
import com.android.builder.model.ApiVersion

buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:2.3.1")
        classpath(kotlinModule("gradle-plugin"))
    }
}

repositories {
    jcenter()
}

apply {
    plugin("com.android.application")
    plugin("kotlin-android")
}

android {
    buildToolsVersion("25.0.0")
    compileSdkVersion(23)

    defaultConfig {
        setMinSdkVersion(15)
        setTargetSdkVersion(23)

        applicationId = "com.example.kotlingradle"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    compile("com.android.support:appcompat-v7:23.4.0")
    compile("com.android.support.constraint:constraint-layout:1.0.0-alpha8")
    compile(kotlinModule("stdlib"))
}

//Extension functions to allow comfortable references
fun DefaultProductFlavor.setMinSdkVersion(value: Int) = setMinSdkVersion(value.asApiVersion())

fun DefaultProductFlavor.setTargetSdkVersion(value: Int) = setTargetSdkVersion(value.asApiVersion())

fun Int.asApiVersion(): ApiVersion = DefaultApiVersion.create(this)
