plugins {
    java
    `jvm-test-suite`
}

repositories {
    mavenCentral()
}

testing {
    suites {
        unitTest {
            // tag::test-categories[]
            useJUnit {
                includeCategories("org.gradle.junit.CategoryA")
                excludeCategories("org.gradle.junit.CategoryB") // FIXME needs JvmTestSuite#useJUnit(Action<? extends JUnitOptions>)
            }
            // end::test-categories[]
            dependencies {
                implementation("junit:junit:4.13")
            }
        }
    }
}
