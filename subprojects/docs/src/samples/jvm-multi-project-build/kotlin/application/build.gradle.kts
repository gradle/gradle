plugins {
    application
}

dependencies {
    implementation(project(":utilities"))
}

application {
    mainClassName = "org.gradle.sample.Main"
}