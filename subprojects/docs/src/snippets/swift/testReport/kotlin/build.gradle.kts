// tag::test-report[]
val testReportData by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("test-report-data"))
    }
}

dependencies {
    testReportData(project(":core"))
    testReportData(project(":util"))
}

tasks.register<TestReport>("testReport") {
    destinationDir = layout.buildDirectory.dir("reports/allTests").get().asFile
    // Use test results from testReportData configuration
    (getTestResultDirs() as ConfigurableFileCollection).from(testReportData)
}
// end::test-report[]
