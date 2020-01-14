dependencies {
    implementation(project(":build"))
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))

    implementation("com.google.code.gson:gson:2.7")
}

gradlePlugin {
    plugins {
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
