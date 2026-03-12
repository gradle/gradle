kotlinDslPlugin {
    description = "Provides plugins that create update tasks for the Gradle build"

    dependencies {
        implementation("gradlebuild:basics")
        implementation("gradlebuild:module-identity")
        implementation(catalog("buildLibs.gson"))
        implementation(catalog("buildLibs.jsoup"))
    }
}

