plugins {
    gradlebuild.internal.java
    gradlebuild.distributions
}

dependencies {
    runtimeOnly(project(":distributionsCore"))

    runtimeOnly(project(":dependencyManagement"))
    runtimeOnly(project(":workers"))
    runtimeOnly(project(":instantExecution"))
    runtimeOnly(project(":compositeBuilds"))
    runtimeOnly(project(":versionControl"))
    runtimeOnly(project(":pluginUse"))

    runtimeOnly(project(":resourcesHttp"))
    runtimeOnly(project(":resourcesSftp"))
    runtimeOnly(project(":resourcesS3"))
    runtimeOnly(project(":resourcesGcs"))
    runtimeOnly(project(":resourcesHttp"))
    runtimeOnly(project(":buildCacheHttp"))

    runtimeOnly(project(":diagnostics"))
    runtimeOnly(project(":reporting"))

    runtimeOnly(project(":toolingApiBuilders"))
    runtimeOnly(project(":kotlinDslToolingBuilders"))

    runtimeOnly(project(":testKit"))
}
