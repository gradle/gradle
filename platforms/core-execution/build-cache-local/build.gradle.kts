plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.jmh")
}

description = "Local build cache implementation"

dependencies {
    api(project(":build-cache"))
    api(libs.inject)

    api(project(":base-annotations"))
    api(project(":base-services"))
    api(project(":build-cache-spi"))
    api(project(":core-api"))
    api(project(":files"))
    api(project(":file-temp"))
    api(project(":functional"))
    api(project(":hashing"))
    api(project(":persistent-cache"))
    api(project(":resources"))

    implementation(libs.guava)
    implementation(libs.h2Database) {
        because("Used in BuildCacheNG")
    }
    implementation(libs.hikariCP) {
        because("Used in BuildCacheNG")
    }

    testImplementation(project(":model-core"))
    testImplementation(project(":file-collections"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":snapshots")))

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
