// tag::configure-directory-build-cache[]
buildCache {
    local<DirectoryBuildCache> {
        directory = File(rootDir, "build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}
// end::configure-directory-build-cache[]

// tag::configure-http-build-cache[]
buildCache {
    remote<HttpBuildCache> {
        url = uri("http://example.com:8123/cache/")
        credentials {
            username = "build-cache-user"
            password = "some-complicated-password"
        }
    }
}
// end::configure-http-build-cache[]
