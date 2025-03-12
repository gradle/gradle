// tag::custom-config[]
plugins {
    id("application")
}

configurations {
    create("instrumentedRuntime") {
        isCanBeConsumed = false
        isCanBeResolved = true
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("instrumented-jar"))
        }
    }
}
// end::custom-config[]

// tag::dependency[]
dependencies {
    add("instrumentedRuntime", project(":producer"))
}
// end::dependency[]

// tag::task[]
tasks.register<JavaExec>("runWithInstrumentation") {
    classpath = configurations["instrumentedRuntime"]
    mainClass.set("com.example.Main")
}
// end::task[]
