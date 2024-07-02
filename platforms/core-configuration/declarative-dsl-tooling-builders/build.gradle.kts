plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Declarative DSL Tooling Builders for IDEs"

dependencies {
    api(projects.serviceProvider)
    api(projects.core)
    api(projects.coreApi)

    api(libs.kotlinStdlib)

    implementation(projects.stdlibJavaExtensions)
    implementation(projects.declarativeDslEvaluator)
    implementation(projects.declarativeDslProvider)
    implementation(projects.declarativeDslToolingModels)

    crossVersionTestDistributionRuntimeOnly(projects.distributionsBasics)
}
