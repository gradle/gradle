buildCache {
    local {
        directory = File(rootDir, "build-cache")
        // Enable reading from the local build cache
        enabled = false
        // Disable writing outputs to the local build cache
        push = false
    }
}
