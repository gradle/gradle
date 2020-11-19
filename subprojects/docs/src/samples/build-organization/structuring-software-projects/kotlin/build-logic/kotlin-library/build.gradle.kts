plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(platform("com.example.platform:plugins-platform"))

    implementation(project(":commons"))
    implementation("org.jetbrains.kotlin.android:org.jetbrains.kotlin.android.gradle.plugin")
}
