plugins {
    `java-library`
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
