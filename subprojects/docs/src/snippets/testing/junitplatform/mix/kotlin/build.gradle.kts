plugins {
    java
}

repositories {
    mavenCentral()
}

// tag::vintage-dependencies[]
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testCompileOnly("junit:junit:4.12")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
}
// end::vintage-dependencies[]

tasks.test {
    useJUnitPlatform()
}
