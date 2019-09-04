import org.gradle.gradlebuild.unittestandcompile.ModuleType

/*
 * A set of general-purpose resource abstractions.
 */
plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":files"))
    implementation(project(":messaging"))
    implementation(project(":native"))

    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_io"))

    testImplementation(project(":processServices"))
    testImplementation(project(":coreApi"))
    testImplementation(project(":fileCollections"))
    testImplementation(project(":snapshots"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":ivy")))
    testImplementation(testFixtures(project(":maven")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":dependencyManagement")))

    integTestImplementation(project(":internalIntegTesting"))
    integTestImplementation(project(":coreApi"))
    integTestImplementation(project(":modelCore"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":resourcesGcs"))
    integTestImplementation(project(":resourcesS3"))
    integTestImplementation(project(":resourcesHttp"))

    integTestImplementation(library("awsS3_core"))
    integTestImplementation(library("awsS3_s3"))
    integTestImplementation(library("joda"))
    integTestImplementation(library("gcs"))
    integTestImplementation(library("commons_io"))
    integTestImplementation(testLibrary("jetty"))
    integTestImplementation(testLibrary("littleproxy"))
    testLibraries("sshd").forEach { integTestImplementation(it) }

    testFixturesImplementation(project(":baseServices"))
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(project(":internalTesting"))
    testFixturesImplementation(project(":internalIntegTesting"))
    testFixturesImplementation(project(":resourcesHttp"))
    testFixturesImplementation(library("slf4j_api"))
    integTestRuntimeOnly(project(":runtimeApiInfo"))
    integTestRuntimeOnly(project(":resourcesSftp"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}
