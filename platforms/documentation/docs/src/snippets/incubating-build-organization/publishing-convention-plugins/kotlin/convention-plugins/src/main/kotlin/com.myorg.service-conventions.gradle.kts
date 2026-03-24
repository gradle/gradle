// Define conventions for service projects this organization.
// Service projects need to use the organization's Java conventions and pass some additional checks

// tag::plugins[]
plugins {
    id("com.myorg.java-conventions")
}
// end::plugins[]

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.7.1")
        }

        val integrationTest by registering(JvmTestSuite::class) {
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

// The organization requires additional documentation in the README for this project
// tag::use-java-class[]
val readmeCheck by tasks.registering(com.example.ReadmeVerificationTask::class) {
    readme = layout.projectDirectory.file("README.md")
    readmePatterns = listOf("^## Service API$")
}
// end::use-java-class[]

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"), readmeCheck)
}
