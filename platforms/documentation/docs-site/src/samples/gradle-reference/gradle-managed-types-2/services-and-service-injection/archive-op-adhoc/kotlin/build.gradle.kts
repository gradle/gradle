interface InjectedArcOps {
    @get:Inject val arcOps: ArchiveOperations
}

tasks.register("myAdHocArchiveOperationsTask") {
    val injected = project.objects.newInstance<InjectedArcOps>()
    val archiveFile = "${project.projectDir}/sources.jar"
    doLast {
        injected.arcOps.zipTree(archiveFile)
    }
}
