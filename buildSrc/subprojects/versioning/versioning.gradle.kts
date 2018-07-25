plugins {
    `java-gradle-plugin`
}

apply(plugin = "org.gradle.kotlin.kotlin-dsl")

dependencies {
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation("com.google.code.gson:gson:2.7")
    implementation(project(":build"))
}

gradlePlugin {
    (plugins) {
        "wrapper" {
            id = "gradlebuild.wrapper"
            implementationClass = "org.gradle.gradlebuild.versioning.WrapperPlugin"
        }
        "updateVersions" {
            id = "gradlebuild.update-versions"
            implementationClass = "org.gradle.gradlebuild.versioning.UpdateVersionsPlugin"
        }
    }
}
