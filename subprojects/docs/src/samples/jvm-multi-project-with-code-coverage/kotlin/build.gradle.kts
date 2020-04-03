plugins {
    `java`
    `jacoco`
}

allprojects {
    version = "1.0.2"
    group = "org.gradle.sample"

    repositories {
        jcenter()
    }
}

// tag::coverageTask[]
// task to gather code coverage from multiple subprojects
tasks.register<JacocoReport>("codeCoverageReport") {
    // If a subproject applies the 'jacoco' plugin, add the result it to the report
    subprojects {
        val subproject = this
        subproject.plugins.withType<JacocoPlugin>().configureEach {
            subproject.tasks.matching({ it.extensions.findByType<JacocoTaskExtension>() != null }).configureEach {
                val testTask = this
                sourceSets(subproject.sourceSets.main.get())
                executionData(testTask)

                // NOTE: the report task does *not* depend on the `test` task by default. Meaning you have to ensure
                // that `test` (or other tasks generating code coverage) run before generating the report.
                // You can achieve this by calling the `check` lifecycle task manually
                // $ ./gradlew test codeCoverageReport

                // Alternatively you can declare a dependency so that the `codeCoverageReport` depends on the `test` tasks.
                // This inevitably enforces that running the `codeCoverageTask` runs the test tasks as well.
                // This may be your intended behavior.
                rootProject.tasks.findByName("codeCoverageReport")!!.dependsOn(testTask)
            }
        }
    }

    // enable the different report types (html, xml, csv)
    reports {
        // xml is usually used to integrate code coverage with
        // other tools like SonarQube, Coveralls or Codecov
        xml.isEnabled = true

        // HTML reports can be used to see code coverage
        // without any external tools
        html.isEnabled = true
    }
}
// end::coverageTask[]
