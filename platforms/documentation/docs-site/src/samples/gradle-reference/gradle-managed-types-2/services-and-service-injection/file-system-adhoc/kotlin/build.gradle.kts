interface InjectedFsOps {
    @get:Inject val fs: FileSystemOperations
}

tasks.register("myAdHocFileSystemOperationsTask") {
    val injected = project.objects.newInstance<InjectedFsOps>()
    doLast {
        injected.fs.copy {
            from("src")
            into("dest")
        }
    }
}
