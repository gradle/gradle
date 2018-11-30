gradle.settingsEvaluated {
    buildCache {
        // vvv Your custom configuration goes here
        remote<HttpBuildCache> {
            setUrl("https://example.com:8123/cache/")
        }
        // ^^^ Your custom configuration goes here
    }
}
