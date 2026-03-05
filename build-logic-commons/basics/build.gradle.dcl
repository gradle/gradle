kotlinBuildLogic {
    description = "Provides plugins for configuring miscellaneous things (repositories, reproducibility, minify)"

    dependencies {
        api("gradlebuild:build-environment")
        api(platform(project(":build-platform")))

        implementation(catalog("buildLibs.guava")) { because("Used by class analysis") }
        implementation(catalog("libs.asm")) { because("Used by class analysis") }
        implementation(catalog("libs.asmCommons")) { because("Used by class analysis") }
        implementation(catalog("buildLibs.kgp")) { because("For manually defined KotlinSourceSet accessor - sourceSets.main.get().kotlin") }

        compileOnly(catalog("buildLibs.kotlinCompilerEmbeddable")) { because("Required by KotlinSourceParser") }

        testImplementation(catalog("testLibs.junit5JupiterEngine"))

        testRuntimeOnly(catalog("testLibs.junitPlatform"))
    }
}
