// tag::apply-plugin[]
plugins {
    xctest
}
// end::apply-plugin[]

// tag::configure-target-machines[]
xctest {
    targetMachines = listOf(machines.linux.x86_64, machines.macOS.x86_64)
}
// end::configure-target-machines[]
