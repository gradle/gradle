plugins {
    application
}

dependencies {
    implementation(project(":utilities"))
}

application {
    mainClass.set("org.gradle.sample.app.Main")
}
