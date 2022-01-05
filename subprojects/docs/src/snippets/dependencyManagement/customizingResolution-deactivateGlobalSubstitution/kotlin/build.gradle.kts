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
    isCanBeResolved = true
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
}
// end::disableGlobalDependencySubstitutionRules[]

dependencies {
    api("org.test:module-a:1.0")
}

tasks.register("resolve") {
    doLast {
        configurations.runtimeClasspath.get().files.forEach { println(it.name) }
        configurations["publishedRuntimeClasspath"].files.forEach { println(it.name) }
    }
}
