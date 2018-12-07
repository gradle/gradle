dependencies {
    implementation("me.champeau.gradle:jmh-gradle-plugin:0.4.7")
    implementation("org.jsoup:jsoup:1.11.3")
    implementation("com.gradle:build-scan-plugin:2.1-rc-1-20181205122938-release")
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation(project(":plugins"))
}

gradlePlugin {
    plugins {
        register("buildscan") {
            id = "gradlebuild.buildscan"
            implementationClass = "org.gradle.gradlebuild.profiling.buildscan.BuildScanPlugin"
        }
    }
}
