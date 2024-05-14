plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Declarative DSL Tooling Builders for IDEs"

dependencies {
    api(project(":base-services"))
    api(project(":core-api"))
    api(project(":declarative-dsl-provider"))

    api(libs.futureKotlin("stdlib"))

    implementation(project(":core"))
    implementation(project(":declarative-dsl-tooling-models"))

    crossVersionTestDistributionRuntimeOnly(project(":distributions-basics"))
}
