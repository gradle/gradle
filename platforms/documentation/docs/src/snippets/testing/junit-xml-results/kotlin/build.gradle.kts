plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13")
}

// tag::configure-location-task[]
tasks.test {
    reports {
        junitXml.outputLocation = layout.buildDirectory.dir("test-junit-xml")
    }
}
// end::configure-location-task[]

// tag::configure-location-convention[]
java.testResultsDir = layout.buildDirectory.dir("junit-xml")
// end::configure-location-convention[]

// tag::configure-content[]
tasks.test {
    reports {
        junitXml.apply {
            includeSystemOutLog = false // defaults to true
            includeSystemErrLog = false // defaults to true
            isOutputPerTestCase = true // defaults to false
            mergeReruns = true // defaults to false
        }
    }
}
// tag::configure-content[]
