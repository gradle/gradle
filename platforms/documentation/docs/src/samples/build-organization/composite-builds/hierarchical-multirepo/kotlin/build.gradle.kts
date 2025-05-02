tasks.register("publishDeps") {
    dependsOn(gradle.includedBuilds.map { it.task(":publishMavenPublicationToMavenRepository") })
}
