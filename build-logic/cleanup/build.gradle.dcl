kotlinDslPlugin {
    description = "Provides a plugin that cleans up after executing tests"

    dependencies {
        implementation("gradlebuild:basics")
        implementation("gradlebuild:module-identity")
    }
}
