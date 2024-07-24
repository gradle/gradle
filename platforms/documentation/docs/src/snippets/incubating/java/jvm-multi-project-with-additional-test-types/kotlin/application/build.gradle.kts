plugins {
    id("myproject.java-conventions")
    application
}

dependencies {
    implementation(project(":list"))
    implementation(project(":utilities"))
}

application {
    mainClass = "org.gradle.sample.app.Main"
}
