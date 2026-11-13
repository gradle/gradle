import org.example.ConfigFileArgumentProvider

plugins {
    java
}

// tag::register-provider[]
tasks.register<JavaExec>("runApp") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "org.example.App"
    jvmArgumentProviders.add(objects.newInstance<ConfigFileArgumentProvider>().apply {
        configFile = layout.projectDirectory.file("app.conf")
    })
}
// end::register-provider[]
