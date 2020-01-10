dependencies {
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation("com.google.code.gson:gson:2.7")
    implementation(project(":build"))
}

gradlePlugin {
    plugins {
        register("buildVersion") {
            id = "gradlebuild.build-version"
            implementationClass = "org.gradle.gradlebuild.versioning.BuildVersionPlugin"
        }
        register("wrapper") {
            id = "gradlebuild.wrapper"
            implementationClass = "org.gradle.gradlebuild.versioning.WrapperPlugin"
        }
        register("updateVersions") {
            id = "gradlebuild.update-versions"
            implementationClass = "org.gradle.gradlebuild.versioning.UpdateVersionsPlugin"
        }
    }
}
