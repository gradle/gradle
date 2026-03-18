plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.testng:testng:6.9.13.3")
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
