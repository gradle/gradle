plugins {
    `java-gradle-plugin`
}

apply { plugin("org.gradle.kotlin.kotlin-dsl") }

dependencies {
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation("com.google.guava:guava-jdk5:14.0.1")
    implementation("org.ow2.asm:asm:6.0")
    implementation("org.ow2.asm:asm-commons:6.0")
}

gradlePlugin {
    (plugins) {
        "minify" {
            id = "gradlebuild.minify"
            implementationClass = "org.gradle.gradlebuild.packaging.MinifyPlugin"
        }
    }
}


