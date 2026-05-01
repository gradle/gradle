plugins {
    id("java-library")
}

repositories {
    mavenCentral()
}

// tag::conditional-reports[]
tasks.withType<Test>().configureEach {
    if (!project.hasProperty("createReports")) {
        reports.html.required = false
        reports.junitXml.required = false
    }
}
// end::conditional-reports[]
