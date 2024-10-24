tasks.withType<PublishToMavenRepository>().configureEach {
    notCompatibleWithConfigurationCache("Configures repository at execution time")
    doFirst {
        val address = com.example.MavenRepositoryStub.start()
        getRepository().getUrl().set(uri(address))
    }
    doLast {
        com.example.MavenRepositoryStub.stop()
    }
}
