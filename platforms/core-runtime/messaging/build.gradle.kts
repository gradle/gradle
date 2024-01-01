plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation of messaging between Gradle processes"

gradlebuildJava.usedInWorkers()

dependencies {
    api(project(":base-annotations"))
    api(project(":hashing"))
    api(project(":base-services"))

    api(libs.fastutil)
    api(libs.jsr305)
    api(libs.slf4jApi)

    implementation(project(":build-operations"))

    implementation(libs.guava)
    implementation(libs.kryo)

    testImplementation(testFixtures(project(":core")))

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(libs.slf4jApi)

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
