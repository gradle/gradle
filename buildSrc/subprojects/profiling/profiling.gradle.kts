dependencies {
    implementation("me.champeau.gradle:jmh-gradle-plugin:0.4.8")
    implementation("org.jsoup:jsoup:1.11.3")
    implementation("com.gradle:build-scan-plugin:2.2")
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation(project(":plugins"))
    implementation(project(":build"))
}

gradlePlugin {
    plugins {
        register("buildscan") {
            id = "gradlebuild.buildscan"
            implementationClass = "org.gradle.gradlebuild.profiling.buildscan.BuildScanPlugin"
        }
    }
}
