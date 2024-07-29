repositories {
    mavenCentral()
}

val scm by configurations.creating

dependencies {
    scm("org.eclipse.jgit:org.eclipse.jgit:4.9.2.201712150930-r")
    scm("commons-codec:commons-codec:1.7")
}

// tag::iteration-task[]
tasks.register("iterateResolvedArtifacts") {
    val scm = configurations["scm"]
    dependsOn(scm)

    val scmArtifacts = scm.map { it.absolutePath }
    doLast {
        scmArtifacts.forEach {
            logger.quiet(it)
        }
    }
}
// end::iteration-task[]
