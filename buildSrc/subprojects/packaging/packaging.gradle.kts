dependencies {
    implementation(project(":docs")) {
        // TODO turn this around: move corresponding code to this project and let docs depend on it
        because("API metadata generation is part of the DSL guide")
    }
    implementation(project(":basics"))
    implementation(project(":moduleIdentity"))
    implementation(project(":jvm"))

    implementation("com.google.guava:guava")
    implementation("org.ow2.asm:asm")
    implementation("org.ow2.asm:asm-commons")
    implementation("com.google.code.gson:gson")

    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        register("minify") {
            id = "gradlebuild.minify"
            implementationClass = "org.gradle.gradlebuild.packaging.MinifyPlugin"
        }
        register("shadedJar") {
            id = "gradlebuild.shaded-jar"
            implementationClass = "org.gradle.gradlebuild.packaging.ShadedJarPlugin"
        }
        register("distributions") {
            id = "gradlebuild.distributions"
            implementationClass = "org.gradle.gradlebuild.packaging.GradleDistributionsPlugin"
        }
        register("install") {
            id = "gradlebuild.install"
            implementationClass = "org.gradle.gradlebuild.packaging.InstallPlugin"
        }
    }
}


