dependencies {
    compileOnly("com.gradle:gradle-enterprise-gradle-plugin:3.3.4")

    implementation(project(":basics"))
    implementation(project(":docs"))
    implementation(project(":jvm"))

    implementation("me.champeau.gradle:jmh-gradle-plugin:0.5.0")
    implementation("org.jsoup:jsoup")
}

gradlePlugin {
    plugins {
        register("buildscan") {
            id = "gradlebuild.buildscan"
            implementationClass = "org.gradle.gradlebuild.profiling.buildscan.BuildScanPlugin"
        }
    }
}
