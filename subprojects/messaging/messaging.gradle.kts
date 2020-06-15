plugins {
    gradlebuild.distribution.`api-java`
}

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":baseServices"))

    implementation(library("fastutil"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("kryo"))

    testImplementation(testFixtures(project(":core")))

    testFixturesImplementation(project(":baseServices"))
    testFixturesImplementation(library("slf4j_api"))

    integTestDistributionRuntimeOnly(project(":distributionsCore"))
}
