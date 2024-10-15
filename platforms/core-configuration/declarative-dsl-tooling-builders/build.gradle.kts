plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Declarative DSL Tooling Builders for IDEs"

dependencies {
    api(projects.serviceProvider)
    api(projects.core)

    api(libs.kotlinStdlib)

    implementation(projects.coreApi)
    implementation(projects.declarativeDslEvaluator)
    implementation(projects.declarativeDslProvider)
    implementation(projects.declarativeDslToolingModels)
    implementation(projects.stdlibJavaExtensions)

    crossVersionTestDistributionRuntimeOnly(projects.distributionsBasics)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
