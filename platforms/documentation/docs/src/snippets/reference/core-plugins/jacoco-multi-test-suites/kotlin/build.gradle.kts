plugins {
    java
    jacoco
}

repositories {
    mavenCentral()
}

testing {
    suites {
        val test = getByName<JvmTestSuite>("test") {
            useJUnitJupiter()
        }
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()
            dependencies {
                implementation(project())
            }
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
        }
    }
}

// tag::multi-test-suites[]
val testTasks = tasks.withType<Test>()

val testCoverageData = files()
testTasks.configureEach {
    val jacocoExt = extensions.getByType<JacocoTaskExtension>()
    testCoverageData.from(provider { jacocoExt.destinationFile })
}

tasks.register<JacocoReport>("codeCoverageReport") {
    dependsOn(testTasks)             // run every test suite first
    executionData(testCoverageData)  // collect each suite's coverage data
    sourceSets(sourceSets.main.get())
}
// end::multi-test-suites[]
