plugins {
    id("com.example.android-application")
}

group = "${group}.android-app"

android {
    namespace = "com.example.myproduct.androidapp"
}

dependencies {
    implementation("com.example.myproduct.user-feature:table")
}
