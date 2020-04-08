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

        val integrationTestTask = tasks.register<Test>("integrationTest") {
            description = "Runs integration tests."
            group = "verification"

            testClassesDirs = integrationTest.output.classesDirs
            classpath = integrationTest.runtimeClasspath
            shouldRunAfter("test")
        }

        tasks.named("check") { dependsOn(integrationTestTask) }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
