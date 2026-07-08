val address = com.example.MavenRepositoryStub.start()
extensions.getByType<PublishingExtension>().repositories.withType<MavenArtifactRepository>().configureEach {
    setUrl(address)
}

tasks.withType<PublishToMavenRepository>().configureEach {
    notCompatibleWithConfigurationCache("Configures repository at execution time")
    doLast {
        com.example.MavenRepositoryStub.stop()
    }
}
