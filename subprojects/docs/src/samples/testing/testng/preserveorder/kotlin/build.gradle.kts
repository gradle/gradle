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
tasks.named<Test>("test") {
    useTestNG {
        preserveOrder = true
    }
}
// end::test-config[]

tasks.named<Test>("test") {
    testLogging.showStandardStreams = true
}
