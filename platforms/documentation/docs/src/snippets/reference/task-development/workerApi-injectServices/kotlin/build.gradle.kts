// tag::register-task[]
tasks.register<CopyDocsTask>("copyDocs") {
    sourceDirectory = layout.projectDirectory.dir("docs")
    outputDirectory = layout.buildDirectory.dir("docs")
}
// end::register-task[]
