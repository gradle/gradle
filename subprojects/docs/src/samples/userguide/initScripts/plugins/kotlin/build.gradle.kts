// tag::show-repos-task[]
repositories{
    mavenCentral()
}

task("showRepositories") {
    doLast {
        repositories.map { it as MavenArtifactRepository }.forEach {
            println("repository: ${it.name} ('${it.url}')")
        }
    }
}
// end::show-repos-task[]
