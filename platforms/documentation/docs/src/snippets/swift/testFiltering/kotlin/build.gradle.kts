plugins {
    xctest
}

// tag::test-filtering[]
xctest {
    binaries.configureEach {
        runTask.get().filter.includeTestsMatching("SomeIntegTest.*") // or `"Testing.SomeIntegTest.*"` on macOS
    }
}
// end::test-filtering[]
