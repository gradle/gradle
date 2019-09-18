import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":workerProcesses"))
    implementation(project(":persistentCache"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":snapshots"))
    implementation(project(":fileCollections"))
    implementation(project(":files"))
    implementation(project(":native"))
    implementation(project(":resources"))

    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("inject"))

    testImplementation(project(":native"))
    testImplementation(project(":fileCollections"))
    testImplementation(project(":resources"))
    testImplementation(project(":snapshots"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))

    testRuntimeOnly(project(":runtimeApiInfo"))
    testRuntimeOnly(project(":dependencyManagement"))

    integTestRuntimeOnly(project(":kotlinDsl"))
    integTestRuntimeOnly(project(":kotlinDslProviderPlugins"))
    integTestRuntimeOnly(project(":apiMetadata"))
    integTestRuntimeOnly(project(":testKit"))

    integTestImplementation(project(":jvmServices"))
    integTestImplementation(project(":internalIntegTesting"))

    testFixturesImplementation(library("inject"))
    testFixturesImplementation(project(":baseServices"))
    testFixturesImplementation(project(":internalTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

