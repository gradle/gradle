// tag::custom-repository[]
repositories {
    maven {
      url = uri("http://repo.mycompany.com/maven2")
    }
}
// end::custom-repository[]

tasks.register("checkRepositories") {
    doLast {
        assert(repositories.size == 1)
        assert(repositories[0] is MavenArtifactRepository)
        assert((repositories[0] as MavenArtifactRepository).url == uri("http://repo.mycompany.com/maven2"))
    }
}
