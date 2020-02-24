plugins {
    application
}

dependencies {
    implementation(project(":list"))
    implementation(project(":utilities"))
    testImplementation("junit:junit:4.12")
}

application {
    mainClassName = "org.gradle.sample.Main"
}