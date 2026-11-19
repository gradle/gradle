// tag::conditional-apply[]
plugins {
    java
    id("com.gradleup.shadow") version "8.3.4" apply false // <1>
}

if (providers.gradleProperty("fatJar").isPresent) { // <2>
    pluginManager.apply("com.gradleup.shadow")
}
// end::conditional-apply[]

// tag::with-plugin[]
pluginManager.withPlugin("com.gradleup.shadow") {
    tasks.named<Jar>("shadowJar") {
        archiveClassifier = "fat"
    }
}
// end::with-plugin[]

// tag::has-plugin[]
tasks.register("printShadowStatus") {
    val shadowApplied = pluginManager.hasPlugin("com.gradleup.shadow")
    doLast {
        println("Shadow plugin applied: $shadowApplied")
    }
}
// end::has-plugin[]

// tag::plugin-container[]
plugins.withType<JavaBasePlugin> {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
// end::plugin-container[]

tasks.register("printEncoding") {
    val encoding = tasks.named<JavaCompile>("compileJava").map { it.options.encoding }
    doLast {
        println("Encoding: ${encoding.get()}")
    }
}
