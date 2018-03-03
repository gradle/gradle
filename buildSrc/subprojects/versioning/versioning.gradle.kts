plugins {
    `java-gradle-plugin`
}

apply { plugin("org.gradle.kotlin.kotlin-dsl") }

dependencies {
    compile(project(":configuration"))
    compile(project(":kotlinDsl"))
    compile("com.google.code.gson:gson:2.7")
    compile(project(":build"))
}

gradlePlugin {
    (plugins) {
        "configureWrapperTasks" {
            id = "configure-wrapper-tasks"
            implementationClass = "org.gradle.gradlebuild.versioning.ConfigureWrapperTasksPlugin"
        }
        "updateVersions" {
            id = "update-versions"
            implementationClass = "org.gradle.gradlebuild.versioning.UpdateVersionsPlugin"
        }
    }
}
