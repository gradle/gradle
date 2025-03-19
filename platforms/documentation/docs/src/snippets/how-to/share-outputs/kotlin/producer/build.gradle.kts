// tag::instrumentedjar-task[]
plugins {
    id("java-library")
}

val instrumentedJar by tasks.registering(Jar::class) {
    archiveClassifier.set("instrumented")
    from(sourceSets.main.get().output)
    // Additional instrumentation processing could go here
}
// end::instrumentedjar-task[]

// tag::custom-config[]
configurations {
    create("instrumentedJars") {
        isCanBeConsumed = true
        isCanBeResolved = false
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("instrumented-jar"))
        }
    }
}

artifacts {
    add("instrumentedJars", instrumentedJar)
}
// end::custom-config[]
