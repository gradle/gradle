plugins {
    java
}

// tag::lazy-javadoc-destination[]
tasks.named<Javadoc>("javadoc") {
    // Lazy variant: wire from another provider without forcing eager evaluation.
    destinationDirectory = layout.buildDirectory.dir("docs/javadoc")
}
// end::lazy-javadoc-destination[]

abstract class VerifyJavadocDestination : DefaultTask() {
    @get:Internal
    abstract val destination: DirectoryProperty

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    @TaskAction
    fun action() {
        val dest = destination.get().asFile
        val projDir = projectDirectory.get().asFile
        println("javadoc destination: ${dest.toRelativeString(projDir)}")
    }
}

tasks.register<VerifyJavadocDestination>("verifyJavadocDestination") {
    destination = tasks.named<Javadoc>("javadoc").flatMap { it.destinationDirectory }
    projectDirectory = layout.projectDirectory
}
