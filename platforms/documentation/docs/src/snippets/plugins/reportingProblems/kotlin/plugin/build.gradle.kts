plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
}

group = "org.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

gradlePlugin {
    // Define the plugin
    val publishingGuard by plugins.creating {
        id = "org.example.pubcheck"
        implementationClass = "org.example.PublishingGuardPlugin"
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
