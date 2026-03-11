kotlinDslPlugin {
    description = "Provides a plugin that configures IntelliJ's idea-ext plugin"

    dependencies {
        implementation("gradlebuild:basics")
        implementation(catalog("buildLibs.ideaExtPlugin"))
    }
}
