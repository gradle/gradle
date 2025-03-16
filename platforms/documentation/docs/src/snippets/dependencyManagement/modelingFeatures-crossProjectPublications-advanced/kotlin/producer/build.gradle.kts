plugins {
    `java-library`
}

// tag::declare-outgoing-configuration[]
val instrumentedJars by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, JavaVersion.current().majorVersion.toInt())
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("instrumented-jar"))
    }
}
// end::declare-outgoing-configuration[]

// tag::declare-task[]
val instrumentedJar = tasks.register("instrumentedJar", Jar::class) {
    archiveClassifier = "instrumented"
}
// end::declare-task[]

// tag::attach-outgoing-artifact[]
artifacts {
    add("instrumentedJars", instrumentedJar)
}
// end::attach-outgoing-artifact[]
