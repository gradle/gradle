plugins {
    id("application")
    id("org.jetbrains.dokka") version "1.9.0"
}

repositories {
    mavenCentral()
}

// tag::app[]
repeat(4) { counter ->
    tasks.register("task$counter") {
        doLast {
            println("I'm task number $counter")
        }
    }
}
tasks.named("task0") { dependsOn("task2", "task3") }
// end::app[]

// tag::hello[]
tasks.register("hello") {
    group = "Custom"
    description = "A lovely greeting task."
    doLast {
        println("Hello world!")
    }
    dependsOn(tasks.assemble)
}
// end::hello[]

// tag::dokka[]
tasks.dokkaHtml.configure {
    outputDirectory.set(buildDir)
}
// end::dokka[]
