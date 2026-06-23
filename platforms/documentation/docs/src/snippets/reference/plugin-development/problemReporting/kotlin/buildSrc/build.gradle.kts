plugins {
    id("java-gradle-plugin")
}

gradlePlugin {
    plugins {
        create("problemReportingPlugin") {
            id = "org.myorg.problem-reporting"
            implementationClass = "org.myorg.ProblemReportingPlugin"
        }
    }
}
