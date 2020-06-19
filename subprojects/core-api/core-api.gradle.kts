import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty

plugins {
    gradlebuild.distribution.`api-java`
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":baseServicesGroovy"))
    implementation(project(":files"))
    implementation(project(":logging"))
    implementation(project(":persistentCache"))
    implementation(project(":processServices"))
    implementation(project(":resources"))

    implementation(library("slf4j_api"))
    implementation(library("groovy"))
    implementation(library("ant"))
    implementation(library("guava"))
    implementation(library("commons_io"))
    implementation(library("commons_lang"))
    implementation(library("inject"))

    testImplementation(library("asm"))
    testImplementation(library("asm_commons"))
    testImplementation(testFixtures(project(":logging")))

    testFixturesImplementation(project(":baseServices"))
}

classycle {
    excludePatterns.set(listOf("org/gradle/**"))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
    ignoreParameterizedVarargType() // [unchecked] Possible heap pollution from parameterized vararg type: ArtifactResolutionQuery, RepositoryContentDescriptor, HasMultipleValues
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
