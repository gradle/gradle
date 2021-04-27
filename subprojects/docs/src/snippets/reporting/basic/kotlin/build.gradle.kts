// tag::apply-reporting-plugin[]
plugins {
    `project-report`
}
// end::apply-reporting-plugin[]

// tag::configure-reporting-plugin[]
projectReports {
    projectReportDir.set(layout.buildDirectory.dir("my-reports"))
}
// end::configure-reporting-plugin[]
