plugins {
    `java-gradle-plugin`
}

apply(plugin = "org.gradle.kotlin.kotlin-dsl")

dependencies {
    implementation(project(":configuration"))
    implementation(project(":build"))
    implementation(project(":kotlinDsl"))
    implementation("com.google.guava:guava:26.0-jre")
    implementation("org.ow2.asm:asm:6.0")
    implementation("org.ow2.asm:asm-commons:6.0")
    implementation("com.google.code.gson:gson:2.7")
    implementation("com.thoughtworks.qdox:qdox:2.0-M9")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.1.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.1.0")
}

tasks.withType(Test::class.java).named("test").configure {
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


