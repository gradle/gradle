rootProject.name = "configure-built-in-caches"
// tag::configure-directory-build-cache[]
buildCache {
    local {
        directory = File(rootDir, "build-cache")
        // enabled = false // Uncomment to disable the local build cache
        // push = false // Uncomment to disable storing outputs in the build cache
    }
}
// end::configure-directory-build-cache[]

// tag::configure-http-build-cache[]
buildCache {
    remote<HttpBuildCache> {
        url = uri("https://example.com:8123/cache/")
        credentials {
            username = "build-cache-user"
            password = "some-complicated-password"
        }
    }
}
// end::configure-http-build-cache[]
