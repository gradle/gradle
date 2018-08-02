plugins {
    `java-gradle-plugin`
}

apply(plugin = "org.gradle.kotlin.kotlin-dsl")

dependencies {
    implementation(project(":configuration"))
    implementation(project(":build"))
    implementation(project(":kotlinDsl"))
    implementation("com.google.guava:guava-jdk5:14.0.1")
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
    (plugins) {
        "minify" {
            id = "gradlebuild.minify"
            implementationClass = "org.gradle.gradlebuild.packaging.MinifyPlugin"
        }
        "shadedJar" {
            id = "gradlebuild.shaded-jar"
            implementationClass = "org.gradle.gradlebuild.packaging.ShadedJarPlugin"
        }
        "apiMetadata" {
            id = "gradlebuild.api-metadata"
            implementationClass = "org.gradle.gradlebuild.packaging.ApiMetadataPlugin"
        }
    }
}


