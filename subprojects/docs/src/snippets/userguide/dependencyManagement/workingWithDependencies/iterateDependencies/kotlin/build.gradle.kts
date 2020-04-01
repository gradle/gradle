repositories {
    jcenter()
}

val scm by configurations.creating

dependencies {
    scm("org.eclipse.jgit:org.eclipse.jgit:4.9.2.201712150930-r")
    scm("commons-codec:commons-codec:1.7")
}

// tag::iteration-task[]
tasks.register("iterateDeclaredDependencies") {
    val dependencyCoordinates = provider {
        configurations["scm"].dependencies.map { "${it.group}:${it.name}:${it.version}" }
    }
    doLast {
        dependencyCoordinates.get().forEach {
            logger.quiet(it)
        }
    }
}
// end::iteration-task[]
