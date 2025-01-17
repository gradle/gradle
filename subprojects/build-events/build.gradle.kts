plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation of build event services and build event types (work item, tasks, tests, configuration, etc)"

dependencies {
    api(projects.concurrent)
    api(projects.stdlibJavaExtensions)
    api(projects.serviceProvider)
    api(projects.buildOperations)
    api(projects.core)
    api(projects.coreApi)
    api(projects.messaging)
    api(projects.toolingApi)
    api(projects.problemsApi)

    implementation(projects.modelCore)

    api(libs.jsr305)

    implementation(libs.errorProneAnnotations)
    implementation(libs.guava)

    testImplementation(projects.internalTesting)
    testImplementation(projects.modelCore)

    integTestImplementation(projects.logging) {
        because("This isn't declared as part of integtesting's API, but should be as logging's classes are in fact visible on the API")
    }
    integTestImplementation(projects.buildOption)
    integTestImplementation(projects.enterpriseOperations)

    integTestDistributionRuntimeOnly(projects.distributionsBasics)  {
        because("Requires ':toolingApiBuilders': Event handlers are in the wrong place, and should live in this project")
    }
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
