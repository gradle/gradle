dependencies {
    implementation(project(":configuration"))
    implementation(project(":build"))
    implementation(project(":kotlinDsl"))
    implementation("com.google.guava:guava:27.1-jre")
    implementation("org.ow2.asm:asm:7.1")
    implementation("org.ow2.asm:asm-commons:7.1")
    implementation("com.google.code.gson:gson:2.7")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.1.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.1.0")
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
        register("apiMetadata") {
            id = "gradlebuild.api-metadata"
            implementationClass = "org.gradle.gradlebuild.packaging.ApiMetadataPlugin"
        }
    }
}


