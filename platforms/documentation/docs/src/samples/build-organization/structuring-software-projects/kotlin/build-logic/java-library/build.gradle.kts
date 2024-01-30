plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(platform("com.example.platform:plugins-platform"))

    implementation(project(":commons"))
}
