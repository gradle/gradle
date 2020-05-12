plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.testng:testng:6.9.4")
}

// tag::test-config[]
tasks.test {
    useTestNG {
        groupByInstances = true
    }
}
// end::test-config[]

tasks.test {
    testLogging.showStandardStreams = true
}
