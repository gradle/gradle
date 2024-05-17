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

// tag::customExec[]
tasks.register<JavaExec>("runOn17") {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(17)
    }

    classpath = sourceSets["main"].runtimeClasspath
    mainClass = application.mainClass
}
// end::customExec[]
