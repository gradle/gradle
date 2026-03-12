kotlinDslPlugin {
    description = "Provides plugins that configure profiling tools (jmh and Build Scan)"

    dependencies {
        implementation(catalog("buildLibs.develocityPlugin"))

        implementation("gradlebuild:basics")
        implementation("gradlebuild:module-identity")

        implementation(project(":documentation"))
        implementation(project(":jvm"))

        implementation(catalog("buildLibs.jmhPlugin"))
    }
}
