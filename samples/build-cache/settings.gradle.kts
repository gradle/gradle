buildCache {
    local {
        isEnabled = true
    }
    remote(DirectoryBuildCache::class.java) {
        setDirectory("path/to/some/local-cache")
        isEnabled = true
        isPush = false
        targetSizeInMB = 1024
    }
    remote(HttpBuildCache::class.java) {
        setUrl("http://example.com/cache")
        isEnabled = false
        isPush = false
        isAllowUntrustedServer = false
        credentials {
            username = "johndoe"
            password = "secret"
        }
    }
}
