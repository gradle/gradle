repositories {
    mavenCentral()
}

val scm by configurations.creating

dependencies {
    scm("org.eclipse.jgit:org.eclipse.jgit:4.9.2.201712150930-r")
    scm("commons-codec:commons-codec:1.7")
}

// tag::iteration-task[]
tasks.register("iterateDeclaredDependencies") {
    doLast {
        val dependencySet = configurations["scm"].dependencies

        dependencySet.forEach {
            logger.quiet("${it.group}:${it.name}:${it.version}")
        }
    }
}
// end::iteration-task[]
