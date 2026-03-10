kotlinDslPlugin {
    description = "Provides a plugin to minify and correct metadata for dependencies used by Gradle"

    dependencies {
        implementation("gradlebuild:basics")
        implementation(catalog("buildLibs.gson"))
    }
}
