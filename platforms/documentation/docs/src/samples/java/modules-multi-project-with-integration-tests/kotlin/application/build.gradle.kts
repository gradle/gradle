plugins {
    id("myproject.java-conventions")
    application
}

dependencies {
    implementation(project(":utilities"))
}

application {
    mainModule = "org.gradle.sample.app"
    mainClass = "org.gradle.sample.app.Main"
}
