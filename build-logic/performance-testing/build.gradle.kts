plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
    id("gradlebuild.build-logic.groovy-dsl-gradle-plugin")
}

dependencies {
    implementation(project(":basics"))
    implementation(project(":module-identity"))
    implementation(project(":integration-testing"))
    implementation(project(":cleanup"))

    implementation("org.openmbee.junit:junit-xml-parser") {
        exclude(module = "lombok") // don't need it at runtime
    }
    implementation("com.google.guava:guava")
    implementation("commons-io:commons-io")
    implementation("javax.activation:activation")
    implementation("javax.xml.bind:jaxb-api")
    implementation("org.gradle:test-retry-gradle-plugin")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("junit:junit")
    testImplementation("io.mockk:mockk")
}

gradlePlugin {
    plugins {
        register("performanceTest") {
            id = "gradlebuild.performance-test"
            implementationClass = "gradlebuild.performance.PerformanceTestPlugin"
        }
    }
}

tasks.compileGroovy.configure {
    classpath = sourceSets.main.get().compileClasspath
}
tasks.compileKotlin.configure {
    classpath += files(tasks.compileGroovy)
}

tasks.withType<Test>().configureEach {
    // This is required for the PerformanceTestIntegrationTest
    environment("BUILD_BRANCH", "myBranch")
    environment("BUILD_COMMIT_ID", "myCommitId")
}
