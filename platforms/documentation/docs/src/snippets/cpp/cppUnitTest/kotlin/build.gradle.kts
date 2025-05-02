// tag::apply-plugin[]
plugins {
    `cpp-unit-test`
}
// end::apply-plugin[]

// tag::configure-target-machines[]
unitTest {
    targetMachines = listOf(machines.linux.x86_64,
        machines.windows.x86, machines.windows.x86_64,
        machines.macOS.x86_64)
}
// end::configure-target-machines[]
