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
    val dependencySet = configurations["scm"].dependencies
    val artifactInfo = dependencySet.map {
        Triple(it.group, it.name, it.version)
    }
    doLast {
        artifactInfo.forEach { (group, name, version) ->
            logger.quiet("$group:$name:$version")
        }
    }
}
// end::iteration-task[]
