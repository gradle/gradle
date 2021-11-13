// tag::test-report[]
plugins {
    id("xctest")
}

extensions.configure<SwiftXCTestSuite>() {
    binaries.configureEach {
        // Disable the test report for the individual test task
        runTask.get().reports.html.required.set(false)
    }
}

configurations.create("binaryTestResultsElements") {
    isCanBeResolved = false
    isCanBeConsumed = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named<Category>(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named<DocsType>("test-report-data"))
    }
    tasks.withType<XCTest>() {
        outgoing.artifact(binaryResultsDirectory)
    }
}
// end::test-report[]
