plugins {
    `kotlin-dsl`
}

description = "Provides plugins for configuring miscellaneous things (repositories, reproducibility, minify)"

group = "gradlebuild"

dependencies {
    api("gradlebuild:build-environment")
    api(platform(projects.buildPlatform))

    implementation(buildLibs.guava) {
        because("Used by class analysis")
    }
    implementation(libs.asm) {
        because("Used by class analysis")
    }
    implementation(libs.asmCommons) {
        because("Used by class analysis")
    }

    compileOnly(buildLibs.kotlinCompilerEmbeddable) {
        because("Required by KotlinSourceParser")
    }
    implementation(buildLibs.kgp) {
        because("For manually defined KotlinSourceSet accessor - sourceSets.main.get().kotlin")
    }

    testImplementation(testLibs.junit5JupiterEngine)

    testRuntimeOnly(testLibs.junitPlatform)
}

tasks.test {
    useJUnitPlatform()
}
