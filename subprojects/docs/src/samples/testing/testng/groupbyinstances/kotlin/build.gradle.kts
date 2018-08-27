plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testCompile("org.testng:testng:6.9.4")
}

// tag::test-config[]
tasks.getByName<Test>("test") {
    useTestNG {
        groupByInstances = true
    }
}
// end::test-config[]

tasks.getByName<Test>("test").testLogging {
    showStandardStreams = true
}
