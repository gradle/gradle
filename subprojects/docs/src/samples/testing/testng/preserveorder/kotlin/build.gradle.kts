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
        (this as TestNGOptions).preserveOrder = true
    }
}
// end::test-config[]

tasks.getByName<Test>("test").testLogging {
    showStandardStreams = true
}
