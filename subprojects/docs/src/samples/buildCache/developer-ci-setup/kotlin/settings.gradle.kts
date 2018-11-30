// tag::developer-ci-setup[]
val isCiServer = System.getenv().containsKey("CI")

buildCache {
    local {
        isEnabled = !isCiServer
    }
    remote<HttpBuildCache> {
        setUrl("https://example.com:8123/cache/")
        isPush = isCiServer
    }
}
// end::developer-ci-setup[]
