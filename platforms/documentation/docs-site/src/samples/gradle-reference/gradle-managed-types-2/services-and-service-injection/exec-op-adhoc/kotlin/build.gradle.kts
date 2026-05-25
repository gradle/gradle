interface InjectedExecOps {
    @get:Inject val execOps: ExecOperations
}

tasks.register("myAdHocExecOperationsTask") {
    val injected = project.objects.newInstance<InjectedExecOps>()

    doLast {
        injected.execOps.exec {
            commandLine("ls", "-la")
        }
    }
}
