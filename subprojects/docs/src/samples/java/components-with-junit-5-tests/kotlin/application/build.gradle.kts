plugins {
    application
}

dependencies {
    implementation(project(":list"))
    implementation(project(":utilities"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
}

application {
    mainClassName = "org.gradle.sample.Main"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
