plugins {
    `java-library`
}

// tag::declare-outgoing-configuration[]
val instrumentedJars by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, namedAttribute(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, namedAttribute(Usage.JAVA_RUNTIME))
        attribute(Bundling.BUNDLING_ATTRIBUTE, namedAttribute(Bundling.EXTERNAL))
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, JavaVersion.current().majorVersion.toInt())
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, namedAttribute("instrumented-jar"))
    }
}

inline fun <reified T: Named> Project.namedAttribute(value: String) = objects.named(T::class.java, value)
// end::declare-outgoing-configuration[]

val instrumentedJar = tasks.register("instrumentedJar", Jar::class) {
    archiveClassifier.set("instrumented")
}

// tag::attach-outgoing-artifact[]
artifacts {
    add("instrumentedJars", instrumentedJar)
}
// end::attach-outgoing-artifact[]
