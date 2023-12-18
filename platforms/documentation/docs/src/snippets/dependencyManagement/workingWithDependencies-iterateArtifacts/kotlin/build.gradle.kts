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

    doLast {
        scm.forEach {
            logger.quiet(it.absolutePath)
        }
    }
}
// end::iteration-task[]
