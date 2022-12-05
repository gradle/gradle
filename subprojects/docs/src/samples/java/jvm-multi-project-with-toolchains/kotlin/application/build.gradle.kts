plugins {
    id("myproject.java-conventions")
    application
}

dependencies {
    implementation(project(":list"))
    implementation(project(":utilities"))
}

application {
    mainClass.set("org.gradle.sample.app.Main")
}

// tag::customExec[]
tasks.register<JavaExec>("runOn17") {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    })

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
}
// end::customExec[]
