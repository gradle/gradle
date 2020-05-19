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

subprojects {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

// tag::coverageTask[]
// task to gather code coverage from multiple subprojects
// NOTE: the `JacocoReport` tasks do *not* depend on the `test` task by default. Meaning you have to ensure
// that `test` (or other tasks generating code coverage) run before generating the report.
// You can achieve this by calling the `test` lifecycle task manually
// $ ./gradlew test codeCoverageReport
tasks.register<JacocoReport>("codeCoverageReport") {
    // If a subproject applies the 'jacoco' plugin, add the result it to the report
    subprojects {
        val subproject = this
        subproject.plugins.withType<JacocoPlugin>().configureEach {
            subproject.tasks.matching({ it.extensions.findByType<JacocoTaskExtension>() != null }).configureEach {
                val testTask = this
                sourceSets(subproject.sourceSets.main.get())
                executionData(testTask)
            }

            // To automatically run `test` every time `./gradlew codeCoverageReport` is called,
            // you may want to set up a task dependency between them as shown below.
            // Note that this requires the `test` tasks to be resolved eagerly (see `forEach`) which
            // may have a negative effect on the configuration time of your build.
            subproject.tasks.matching({ it.extensions.findByType<JacocoTaskExtension>() != null }).forEach {
                rootProject.tasks["codeCoverageReport"].dependsOn(it)
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
