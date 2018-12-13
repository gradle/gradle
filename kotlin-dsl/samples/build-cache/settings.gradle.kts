buildCache {
    local {
        isEnabled = false
    }
    local(DirectoryBuildCache::class) {
        directory = file("path/to/some/local-cache")
        isEnabled = true
        isPush = true
        removeUnusedEntriesAfterDays = 2
    }
    remote(HttpBuildCache::class) {
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
