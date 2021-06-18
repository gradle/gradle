plugins {
    id("java-base") // we only apply 'java-base' as this plugin is for projects without source code
    id("jacoco")
}

// Register a code coverage report task to generate the aggregated report
val codeCoverageReport by tasks.registering(AggregatedJacocoReport::class) {
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}

// Make JaCoCo report generation part of the 'check' lifecycle phase
tasks.named("check") {
    dependsOn(codeCoverageReport)
}
