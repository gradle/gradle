plugins {
    java
}

repositories {
    mavenCentral()
}

// tag::vintage-dependencies[]
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testCompileOnly("junit:junit:4.13")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
}
// end::vintage-dependencies[]

tasks.test {
    useJUnitPlatform()
}
