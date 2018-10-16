// tag::apply-plugin[]
plugins {
    // end::apply-plugin[]
    java
// tag::apply-plugin[]
    jacoco
}
// end::apply-plugin[]

// tag::jacoco-configuration[]
jacoco {
    toolVersion = "0.8.2"
    reportsDir = file("$buildDir/customJacocoReportDir")
}
// end::jacoco-configuration[]

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.+")
}

// tag::testtask-configuration[]
tasks.test {
    extensions.configure(JacocoTaskExtension::class) {
        destinationFile = file("$buildDir/jacoco/jacocoTest.exec")
        classDumpDir = file("$buildDir/jacoco/classpathdumps")
    }
}
// end::testtask-configuration[]


// tag::report-configuration[]
tasks.jacocoTestReport {
    reports {
        xml.isEnabled = false
        csv.isEnabled = false
        html.destination = file("${buildDir}/jacocoHtml")
    }
}
// end::report-configuration[]

// tag::violation-rules-configuration[]
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.5".toBigDecimal()
            }
        }

        rule {
            enabled = false
            element = "CLASS"
            includes = listOf("org.gradle.*")

            limit {
                counter = "LINE"
                value = "TOTALCOUNT"
                maximum = "0.3".toBigDecimal()
            }
        }
    }
}
// end::violation-rules-configuration[]
