// tag::avoid-this[]
abstract class BadTask : org.gradle.api.internal.AbstractTask() { // <1>
    @TaskAction
    fun run() {
        logger.lifecycle("Hello")
    }
}

tasks.register<BadTask>("badTask")
// end::avoid-this[]

// tag::do-this[]
abstract class GoodTask : org.gradle.api.DefaultTask() { // <2>
    @TaskAction
    fun run() {
        logger.lifecycle("Hello")
    }
}

tasks.register<GoodTask>("goodTask")
// end::do-this[]
