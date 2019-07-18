dependencies {
    implementation(project(":binaryCompatibility"))
    implementation(project(":build"))
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation(project(":versioning"))
    implementation(project(":performance"))
    implementation("org.jsoup:jsoup:1.11.3")
    implementation("com.google.guava:guava-jdk5:14.0.1")
    implementation("org.ow2.asm:asm:7.1")
    implementation("org.ow2.asm:asm-commons:7.1")
    implementation("com.google.code.gson:gson:2.7")
    testImplementation("junit:junit:4.12")
    testImplementation("io.mockk:mockk:1.8.13")
}

gradlePlugin {
    plugins {
        register("buildTypes") {
            id = "gradlebuild.build-types"
            implementationClass = "org.gradle.plugins.buildtypes.BuildTypesPlugin"
        }
        register("performanceTest") {
            id = "gradlebuild.performance-test"
            implementationClass = "org.gradle.plugins.performance.PerformanceTestPlugin"
        }
        register("unitTestAndCompile") {
            id = "gradlebuild.unittest-and-compile"
            implementationClass = "org.gradle.gradlebuild.unittestandcompile.UnitTestAndCompilePlugin"
        }
    }
}

tasks.withType<Test> {
    environment("BUILD_BRANCH", "myBranch")
}
