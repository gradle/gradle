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
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
// end::declare-junit-dependency[]

repositories {
    mavenCentral()
}
