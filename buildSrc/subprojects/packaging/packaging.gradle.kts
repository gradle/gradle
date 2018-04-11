plugins {
    `java-gradle-plugin`
}

apply { plugin("org.gradle.kotlin.kotlin-dsl") }

dependencies {
    implementation(project(":configuration"))
    implementation(project(":build"))
    implementation(project(":kotlinDsl"))
    implementation("com.google.guava:guava-jdk5:14.0.1")
    implementation("org.ow2.asm:asm:6.0")
    implementation("org.ow2.asm:asm-commons:6.0")
    implementation("com.thoughtworks.qdox:qdox:2.0-M8")
}

gradlePlugin {
    (plugins) {
        "minify" {
            id = "gradlebuild.minify"
            implementationClass = "org.gradle.gradlebuild.packaging.MinifyPlugin"
        }
        "parameter-names" {
            id = "gradlebuild.parameter-names"
            implementationClass = "org.gradle.gradlebuild.packaging.ParameterNamesPlugin"
        }
    }
}


