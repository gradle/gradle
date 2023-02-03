plugins {
    java
}

repositories {
    mavenCentral()
}

// tag::vintage-dependencies[]
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testCompileOnly("junit:junit:4.13")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
}
// end::vintage-dependencies[]

tasks.named<Test>("test") {
    useJUnitPlatform()
}
