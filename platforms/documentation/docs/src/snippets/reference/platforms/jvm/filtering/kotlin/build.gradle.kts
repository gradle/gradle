plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13")
}

// tag::test-filtering[]
tasks.test {
    filter {
        //include specific method in any of the tests
        includeTestsMatching("*UiCheck")

        //include all tests from package
        includeTestsMatching("org.gradle.internal.*")

        //include all integration tests
        includeTestsMatching("*IntegTest")
    }
}
// end::test-filtering[]
