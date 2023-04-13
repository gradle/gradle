plugins {
    `java-library`
}
repositories {
    mavenCentral()
}

// tag::disableGlobalDependencySubstitutionRules[]
configurations.create("publishedRuntimeClasspath") {
    resolutionStrategy.useGlobalDependencySubstitutionRules.set(false)

    extendsFrom(configurations.runtimeClasspath.get())
    isCanBeConsumed = false
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
}
// end::disableGlobalDependencySubstitutionRules[]

dependencies {
    api("org.test:module-a:1.0")
}

tasks.register("resolve") {
    val runtimeClasspath: FileCollection = configurations.runtimeClasspath.get()
    val publishedRuntimeClasspath: FileCollection = configurations["publishedRuntimeClasspath"]

    doLast {
        runtimeClasspath.files.forEach { println(it.name) }
        publishedRuntimeClasspath.files.forEach { println(it.name) }
    }
}
