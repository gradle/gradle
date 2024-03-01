rootProject.name = "developer-ci-setup"
// tag::developer-ci-setup[]
val isCiServer = System.getenv().containsKey("CI")

buildCache {
    remote<HttpBuildCache> {
        url = uri("https://example.com:8123/cache/")
        isPush = isCiServer
    }
}
// end::developer-ci-setup[]
