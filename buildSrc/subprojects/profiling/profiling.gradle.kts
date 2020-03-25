dependencies {
    compileOnly("com.gradle:gradle-enterprise-gradle-plugin:3.2")

    implementation(project(":configuration"))
    implementation(project(":docs"))
    implementation(project(":kotlinDsl"))
    implementation(project(":plugins"))

    implementation("me.champeau.gradle:jmh-gradle-plugin:0.5.0")
    implementation("org.jsoup:jsoup:1.11.3")
}

gradlePlugin {
    plugins {
        register("buildscan") {
            id = "gradlebuild.buildscan"
            implementationClass = "org.gradle.gradlebuild.profiling.buildscan.BuildScanPlugin"
        }
    }
}
