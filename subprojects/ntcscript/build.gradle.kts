plugins {
    id("gradlebuild.distribution.api-kotlin")
}

description = "Non-Turing Complete DSL Provider"

dependencies {

    implementation(kotlin("stdlib-jdk8"))

    implementation(project(":base-services"))
    implementation(project(":core"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":resources"))

    implementation(libs.asm)
    implementation(libs.groovy)
    implementation(libs.inject)
    implementation(libs.slf4jApi)
    implementation(libs.tomlj)

    integTestImplementation(project(":internal-testing"))

    integTestImplementation(libs.guava)

    testFixturesImplementation(project(":internal-integ-testing"))

    testFixturesImplementation(libs.junit)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}

classycle {
    excludePatterns.add("org/gradle/ntcscript/**")
}

testFilesCleanup.reportOnly.set(true)
