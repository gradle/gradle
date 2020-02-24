import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":files"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":fileCollections"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":platformBase"))
    implementation(project(":platformJvm"))

    implementation(library("groovy")) // for 'Task.property(String propertyName) throws groovy.lang.MissingPropertyException'
    implementation(library("guava"))
    implementation(library("inject"))

    testImplementation(project(":native"))
    testImplementation(project(":resources"))
    testImplementation(project(":snapshots"))
    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":languageJava"))
    testRuntimeOnly(project(":runtimeApiInfo"))

    testFixturesImplementation(library("commons_lang"))
    testFixturesImplementation(library("guava"))
    testFixturesImplementation(project(":internalIntegTesting"))
    testFixturesImplementation(testFixtures(project(":core")))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

