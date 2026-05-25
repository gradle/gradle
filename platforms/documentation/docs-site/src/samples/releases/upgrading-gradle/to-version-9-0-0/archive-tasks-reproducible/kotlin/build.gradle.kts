tasks.withType<AbstractArchiveTask>().configureEach {
    // Make file order based on the file system
    isReproducibleFileOrder = false
    // Use file timestamps from the file system
    isPreserveFileTimestamps = true
    // Use permissions from the file system
    useFileSystemPermissions()
}
