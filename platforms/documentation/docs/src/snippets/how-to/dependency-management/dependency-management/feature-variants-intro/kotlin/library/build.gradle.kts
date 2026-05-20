// tag::producer[]
plugins {
    id("java-library")
}

java {
    registerFeature("jsonSupport") {
        usingSourceSet(sourceSets.create("jsonSupport"))
    }
}

dependencies {
    "jsonSupportApi"("com.fasterxml.jackson.core:jackson-databind:2.16.0")
}
// end::producer[]

repositories {
    mavenCentral()
}

group = "org.example"
