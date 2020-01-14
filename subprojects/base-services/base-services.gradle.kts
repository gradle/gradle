/*
 * A set of generic services and utilities.
 *
 * Should have a very small set of dependencies, and should be appropriate to embed in an external
 * application (eg as part of the tooling API).
 */

import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    `java-library`
    gradlebuild.classycle
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
}

dependencies {
    api(project(":baseAnnotations"))
    api(project(":hashing"))

    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("commons_io"))
    implementation(library("asm"))

    integTestImplementation(project(":logging"))

    testFixturesImplementation(library("guava"))
    testImplementation(testFixtures(project(":core")))
    testRuntimeOnly(library("xerces"))

    integTestRuntimeOnly(project(":runtimeApiInfo"))

    jmh("org.bouncycastle:bcprov-jdk15on:1.61")
    jmh("com.google.guava:guava:27.1-android")
}

jmh {
    include = listOf("HashingAlgorithmsBenchmark")
}

val buildReceiptPackage = "/org/gradle/"
val buildReceiptResource = tasks.register<Copy>("buildReceiptResource") {
    from(Callable { tasks.getByPath(":createBuildReceipt").outputs.files })
    destinationDir = file("${gradlebuildJava.generatedResourcesDir}/$buildReceiptPackage")
}

sourceSets.main {
    output.dir(
        gradlebuildJava.generatedResourcesDir,
        "builtBy" to buildReceiptResource
    )
}
