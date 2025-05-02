plugins {
    id("java")
    id("com.example.jacoco")
}

group = "com.example.myproduct"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}

dependencies {
    implementation(platform("com.example.platform:product-platform"))

    testImplementation(platform("com.example.platform:test-platform"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
