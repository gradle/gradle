kotlinDslPlugin {
    description = "Provides a plugin to generate samples using internal build init APIs"

    dependencies {
        implementation(project(":jvm"))
        implementation("gradlebuild:basics")
        implementation(catalog("buildLibs.gradleGuidesPlugin"))
    }
}
