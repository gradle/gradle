plugins {                                                               // <1>
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    id("application")
}

repositories {                                                          // <2>
    mavenCentral()
}

dependencies {                                                          // <3>
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.google.guava:guava:32.1.1-jre")
}

application {                                                           // <4>
    mainClass = "com.example.Main"
}

tasks.named<Test>("test") {                                             // <5>
    useJUnitPlatform()
}
