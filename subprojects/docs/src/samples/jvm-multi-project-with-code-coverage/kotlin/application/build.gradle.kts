plugins {
    application
    jacoco
}

dependencies {
    implementation(project(":list"))
    implementation(project(":utilities"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

application {
    mainClass.set("org.gradle.sample.Main")
}
