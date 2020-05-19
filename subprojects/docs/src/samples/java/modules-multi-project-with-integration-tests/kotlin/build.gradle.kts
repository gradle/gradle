subprojects {
    version = "1.0.2"
    group = "org.gradle.sample"

    repositories {
        jcenter()
    }

    plugins.withType<JavaPlugin>().configureEach {
        configure<JavaPluginExtension> {
            modularity.inferModulePath.set(true)
        }

        val integrationTest by the<SourceSetContainer>().creating

        configurations["integrationTestImplementation"].extendsFrom(configurations["implementation"])
        configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["runtimeOnly"])

        dependencies {
            "integrationTestImplementation"(project(path))
            "integrationTestImplementation"("org.junit.jupiter:junit-jupiter-api:5.6.1")
            "integrationTestRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine")
        }

        val integrationTestJarTask = tasks.register<Jar>(integrationTest.jarTaskName) {
            archiveClassifier.set("integration-tests")
            from(integrationTest.output)
        }
        val integrationTestTask = tasks.register<Test>("integrationTest") {
            description = "Runs integration tests."
            group = "verification"

            testClassesDirs = integrationTest.output.classesDirs
            // Make sure we run the 'Jar' containing the tests (and not just the 'classes' folder) so that test resources are also part of the test module
            classpath = configurations[integrationTest.runtimeClasspathConfigurationName] + files(integrationTestJarTask)
            shouldRunAfter("test")
        }

        tasks.named("check") { dependsOn(integrationTestTask) }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
