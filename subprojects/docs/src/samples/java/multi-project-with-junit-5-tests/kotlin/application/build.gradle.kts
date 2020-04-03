plugins {
    application
}

dependencies {
    implementation(project(":utilities"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

application {
    mainClassName = "org.gradle.sample.app.Main"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
