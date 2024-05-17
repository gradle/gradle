plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "API extraction for Java"

errorprone {
    disabledChecks.addAll(
        "EmptyBlockTag", // 2 occurrences
        "NonApiType", // 1 occurrences
        "ProtectedMembersInFinalClass", // 1 occurrences
    )
}

dependencies {
    api(project(":hashing"))
    api(project(":files"))
    api(project(":snapshots"))

    api(libs.jsr305)
    api(libs.asm)
    api(libs.guava)

    implementation(projects.javaLanguageExtensions)
    implementation(project(":functional"))

    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)

    testImplementation(project(":base-services"))
    testImplementation(project(":internal-testing"))
    testImplementation(testFixtures(project(":snapshots")))
}
