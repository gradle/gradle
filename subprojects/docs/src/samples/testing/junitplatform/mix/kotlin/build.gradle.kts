plugins {
    java
}

repositories {
    mavenCentral()
}

// tag::vintage-dependencies[]
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.1.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.1.0")
    testCompileOnly("junit:junit:4.12")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.1.0")
}
// end::vintage-dependencies[]
