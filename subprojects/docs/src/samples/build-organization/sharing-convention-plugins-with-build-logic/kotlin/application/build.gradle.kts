plugins {
    id("myproject.java-conventions")
    id("com.example.plugin.greeting")
    id("application")
}

dependencies {
    implementation(project(":utilities"))
}

application {
    mainClass.set("org.gradle.sample.app.Main")
}
