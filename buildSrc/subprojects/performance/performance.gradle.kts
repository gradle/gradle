plugins {
    `groovy-gradle-plugin` // Support pre-compiled Groovy script plugins
}

dependencies {
    implementation(project(":basics"))
    implementation(project(":moduleIdentity"))
    api(project(":integrationTesting"))
    implementation(project(":cleanup"))

    implementation("org.codehaus.groovy.modules.http-builder:http-builder:0.7.2")
    implementation("org.openmbee.junit:junit-xml-parser:1.0.0") {
        // don't need it at runtime
        exclude(module = "lombok")
    }
    implementation("commons-io:commons-io:2.6")
    implementation("javax.activation:activation:1.1.1")
    implementation("javax.xml.bind:jaxb-api:2.2.12")
    implementation("com.google.guava:guava")

    testImplementation("org.junit.jupiter:junit-jupiter-params:5.6.2")
    testImplementation("junit:junit:4.13")
    testImplementation("io.mockk:mockk:1.8.13")
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
