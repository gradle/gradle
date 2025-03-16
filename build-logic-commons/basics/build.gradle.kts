plugins {
    `kotlin-dsl`
}

description = "Provides plugins for configuring miscellaneous things (repositories, reproducibility, minify)"

group = "gradlebuild"

dependencies {
    api("gradlebuild:build-environment")
    api(platform(projects.buildPlatform))

    implementation("com.google.guava:guava") {
        because("Used by class analysis")
    }
    implementation("org.ow2.asm:asm") {
        because("Used by class analysis")
    }
    implementation("org.ow2.asm:asm-commons") {
        because("Used by class analysis")
    }

    implementation(kotlin("compiler-embeddable") as String) {
        because("Required by KotlinSourceParser")
    }
    implementation(kotlin("gradle-plugin") as String) {
        because("For manually defined KotlinSourceSet accessor - sourceSets.main.get().kotlin")
    }

    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}
