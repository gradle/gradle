plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Declarative DSL Tooling Builders for IDEs"

dependencies {
    api(project(":core"))
    api(project(":core-api"))
    api(project(":service-provider"))

    api(libs.futureKotlin("stdlib"))

    implementation(project(":declarative-dsl-provider"))
    implementation(project(":declarative-dsl-tooling-models"))

    crossVersionTestDistributionRuntimeOnly(project(":distributions-basics"))
}
