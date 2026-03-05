kotlinBuildLogic {
    description = "Provides plugins for configuring miscellaneous things (repositories, reproducibility, minify)"

    dependencies {
        api("gradlebuild:build-environment")
        api(platform(project(":build-platform")))

        implementation(catalog("buildLibs.guava")) { because("Used by class analysis") }
        // FIXME asm dependencies are declared in distribution.toml which is not accessible via the catalog() function
        // implementation(catalog("buildLibs.asm"))
        // implementation(catalog("buildLibs.asmCommons"))
        implementation("org.ow2.asm:asm:9.9") { because("Used by class analysis") }
        implementation("org.ow2.asm:asm-commons:9.9") { because("Used by class analysis") }
        implementation(catalog("buildLibs.kgp")) { because("For manually defined KotlinSourceSet accessor - sourceSets.main.get().kotlin") }

        compileOnly(catalog("buildLibs.kotlinCompilerEmbeddable")) { because("Required by KotlinSourceParser") }

        testImplementation(catalog("testLibs.junit5JupiterEngine"))

        testRuntimeOnly(catalog("testLibs.junitPlatform"))
    }
}
