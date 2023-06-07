plugins {
    java
}

version = "1.0.2"
group = "com.example"

repositories {
    mavenCentral()
}

// tag::toolchain[]
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}
// end::toolchain[]

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
