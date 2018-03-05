plugins {
    `java-gradle-plugin`
}

apply { plugin("org.gradle.kotlin.kotlin-dsl") }

dependencies {
    implementation(project(":binaryCompatibility"))
    implementation(project(":cleanup"))
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation(project(":profiling"))
    implementation(project(":testing"))
    implementation(project(":plugins"))
}

gradlePlugin {
    (plugins) {
        "javaProjects" {
            id = "gradlebuild.java-projects"
            implementationClass = "org.gradle.gradlebuild.uberplugins.JavaProjectsPlugin"
        }
    }
}
