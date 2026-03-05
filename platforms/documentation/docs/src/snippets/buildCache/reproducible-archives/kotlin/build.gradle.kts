tasks.register<Zip>("createZip") {
    preserveFileTimestamps = false
    reproducibleFileOrder = true
    dirPermissions { unix("755") }
    filePermissions { unix("644") }
    // ...
}
