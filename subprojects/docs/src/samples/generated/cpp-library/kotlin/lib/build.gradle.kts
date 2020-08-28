
plugins {
    `cpp-library` // <1>

    `cpp-unit-test` // <2>
}

library {
    targetMachines.add(machines.linux.x86_64) // <3>
}
