plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Declarative DSL Tooling Builders for IDEs"

dependencies {
    api(project(":base-services"))
    api(project(":declarative-dsl-provider"))
    api(project(":ide"))

    crossVersionTestDistributionRuntimeOnly(project(":distributions-basics"))
}
