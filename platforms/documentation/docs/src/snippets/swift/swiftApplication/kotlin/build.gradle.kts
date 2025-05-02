// tag::apply-plugin[]
plugins {
    `swift-application`
}
// end::apply-plugin[]

// tag::configure-target-machines[]
application {
    targetMachines = listOf(machines.linux.x86_64, machines.macOS.x86_64)
}
// end::configure-target-machines[]
