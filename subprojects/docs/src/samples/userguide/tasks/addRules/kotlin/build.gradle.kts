// tag::all[]
// tag::task-rule[]
tasks.addRule("Pattern: ping<ID>") {
    val taskName = this
    if (startsWith("ping")) {
        task(taskName) {
            doLast {
                println("Pinging: " + (taskName - "ping"))
            }
        }
    }
}
// end::task-rule[]

task("groupPing") {
    dependsOn("pingServer1", "pingServer2")
}
// end::all[]
