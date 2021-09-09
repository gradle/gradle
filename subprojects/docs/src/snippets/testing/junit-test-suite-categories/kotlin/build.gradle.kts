plugins {
    java
    `jvm-test-suite`
}

repositories {
    mavenCentral()
}

testing {
    suites {
        named<JvmTestSuite>("unitTest") { // FIXME TestSuite with name 'unitTest' not found.
            useJUnit()
            dependencies {
                implementation("junit:junit:4.13")
            }
            targets {
                all {
                    // tag::test-categories[]
                    testTask.configure {
                        options { // FIXME Ugly unsafe casts. Do we need to qualify the type of options below?
                            (this as JUnitOptions).includeCategories("org.gradle.junit.CategoryA")
                            (this as JUnitOptions).excludeCategories("org.gradle.junit.CategoryB")
                        }
                    }
                    // end::test-categories[]
                }
            }
        }
    }
}
