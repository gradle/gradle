plugins {
    id("java")
}

repositories {
    mavenCentral()
}

// tag::do-this[]
dependencies {
    implementation("com.google.guava:guava:32.1.2-jre")
}
// end::do-this[]
