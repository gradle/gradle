// tag::show-repos-task[]
repositories{
    mavenCentral()
}

tasks.register("showRepositories") {
    val repositoryData = repositories.withType<MavenArtifactRepository>().map { it.name to it.url }.toMap()
    doLast {
        repositoryData.forEach {
            println("repository: ${it.key} ('${it.value}')")
        }
    }
}
// end::show-repos-task[]
