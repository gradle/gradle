plugins {
    `java-library`
}

// tag::declare-outgoing-configuration[]
val instrumentedJars by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    // If you want this configuration to share the same dependencies, otherwise omit this line
    extendsFrom(configurations["implementation"], configurations["runtimeOnly"])
}
// end::declare-outgoing-configuration[]

val instrumentedJar = tasks.register("instrumentedJar", Jar::class) {
    archiveClassifier = "instrumented"
}

// tag::attach-outgoing-artifact[]
artifacts {
    add("instrumentedJars", instrumentedJar)
}
// end::attach-outgoing-artifact[]

/*
// tag::attach-outgoing-artifact-explicit[]
artifacts {
    add("instrumentedJars", someTask.outputFile) {
        builtBy(someTask)
    }
}
// end::attach-outgoing-artifact-explicit[]
*/

