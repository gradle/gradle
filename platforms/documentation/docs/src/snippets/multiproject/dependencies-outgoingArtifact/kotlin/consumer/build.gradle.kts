plugins {
    id("java-library")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13")
}

// tag::producer-project-dependency[]
dependencies {
    runtimeOnly(project(":producer"))
}
// end::producer-project-dependency[]
