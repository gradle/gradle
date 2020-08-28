
plugins {
    `swift-application` // <1>

    xctest // <2>
}

application { // <3>
    targetMachines.add(machines.macOS.x86_64) // <4>
}
