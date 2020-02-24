plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.junit.jupiter:junit-jupiter-api:5.1.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.1.0")
}

// tag::test-tags[]
tasks.test {
    useJUnitPlatform {
        includeTags("fast")
        excludeTags("slow")
    }
}
// end::test-tags[]
