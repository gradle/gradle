plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.testng:testng:6.3.1")
}

// tag::test-config[]
tasks.named<Test>("test") {
    useTestNG {
        val options = this as TestNGOptions
        options.excludeGroups("integrationTests")
        options.includeGroups("unitTests")
    }
}
// end::test-config[]
