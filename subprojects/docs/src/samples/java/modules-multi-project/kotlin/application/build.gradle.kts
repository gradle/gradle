plugins {
    id("myproject.java-conventions")
    application
}

dependencies {
    implementation(project(":utilities"))
}

application {
    mainModule.set("org.gradle.sample.app")
    mainClass.set("org.gradle.sample.app.Main")
}
