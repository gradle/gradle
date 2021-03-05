plugins {
    java
}

// tag::declare-gradle-testkit-dependency[]
dependencies {
    testImplementation(gradleTestKit())
}
// end::declare-gradle-testkit-dependency[]

// tag::declare-junit-dependency[]
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
// end::declare-junit-dependency[]

repositories {
    mavenCentral()
}
