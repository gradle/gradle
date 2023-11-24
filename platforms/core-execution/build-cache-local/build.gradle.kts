plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.jmh")
}

description = "Local build cache implementation"

dependencies {
    api(project(":build-cache"))
    api(project(":snapshots"))

    implementation(project(":build-cache-packaging"))
    implementation(project(":base-services"))
    implementation(project(":enterprise-operations"))
    implementation(project(":core-api"))
    implementation(project(":files"))
    implementation(project(":file-temp"))
    implementation(project(":functional"))
    implementation(project(":native"))
    implementation(project(":persistent-cache"))
    implementation(project(":resources"))
    implementation(project(":logging"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.h2Database) {
        because("Used in BuildCacheNG")
    }
    implementation(libs.hikariCP) {
        because("Used in BuildCacheNG")
    }
    implementation(libs.gson) {
        because("Used in Build Cache NG: Cache manifest uses JSON format")
    }
    implementation(libs.commonsIo)

    testImplementation(project(":model-core"))
    testImplementation(project(":file-collections"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":snapshots")))

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
