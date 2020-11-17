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
test {
    reporting {
        junitXml.outputLocation.set(projectLayout.buildDirectory.dir("test-junit-xml"))
    }
}
// end::configure-location-task[]

// tag::configure-location-convention[]
project.setProperty("testResultsDirName", "$buildDir/junit-xml")
// end::configure-location-convention[]

// tag::configure-content[]
test {
    reporting {
        junitXml {
            outputPerTestCase = true // defaults to false
            mergeReruns.set(true) // defaults to false
        }
    }
}
// tag::configure-content[]
