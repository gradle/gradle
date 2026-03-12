kotlinDslPlugin {
    description = "Provides a plugin to define entry point lifecycle tasks used for development (e.g., sanityCheck)"

    dependencies {
        implementation("gradlebuild:basics")

        implementation(catalog("buildLibs.gson"))
    }
}
