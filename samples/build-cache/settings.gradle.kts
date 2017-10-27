buildCache {
    local {
        isEnabled = true
    }
    remote(DirectoryBuildCache::class.java) {
        directory = file("path/to/some/local-cache")
        isEnabled = true
        isPush = false
        targetSizeInMB = 1024
    }
    remote(HttpBuildCache::class.java) {
        url = uri("http://example.com/cache")
        isEnabled = false
        isPush = false
        isAllowUntrustedServer = false
        credentials {
            username = "johndoe"
            password = "secret"
        }
    }
}
