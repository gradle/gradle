
plugins {
    `cpp-application` // <1>

    `cpp-unit-test` // <2>
}

application { // <3>
    targetMachines.add(machines.linux.x86_64)
}
