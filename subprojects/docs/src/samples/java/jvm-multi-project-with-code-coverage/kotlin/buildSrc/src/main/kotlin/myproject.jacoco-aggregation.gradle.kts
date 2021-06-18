plugins {
    id("java-base")
    id("jacoco")
}

repositories {
    mavenCentral()
}

// Task to gather code coverage from multiple subprojects
val codeCoverageReport by tasks.registering(AggregatedJacocoReport::class) {
    reports {
        // xml is usually used to integrate code coverage with
        // other tools like SonarQube, Coveralls or Codecov
        xml.required.set(true)

        // HTML reports can be used to see code coverage
        // without any external tools
        html.required.set(true)
    }
}

// Make JaCoCo report generation part of the 'check' lifecycle phase
tasks.check {
    dependsOn(codeCoverageReport)
}
