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
    api(project(":pineapple"))
    api(library("jsr305"))

    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("commons_io"))
    implementation(library("asm"))

    jmhImplementation(library("bouncycastle_provider")) {
        version {
            prefer(libraryVersion("bouncycastle_provider"))
        }
    }

    integTestImplementation(project(":logging"))
    
    testFixturesImplementation(library("guava"))
    testImplementation(testFixtures(project(":core")))
    testRuntimeOnly(library("xerces"))
    
    integTestRuntimeOnly(project(":runtimeApiInfo"))
}

jmh {
    include = listOf("HashingAlgorithmsBenchmark")
}

val buildReceiptPackage: String by rootProject.extra

val buildReceiptResource = tasks.register<Copy>("buildReceiptResource") {
    from(Callable { tasks.getByPath(":createBuildReceipt").outputs.files })
    destinationDir = file("${gradlebuildJava.generatedResourcesDir}/$buildReceiptPackage")
}

sourceSets.main { output.dir(gradlebuildJava.generatedResourcesDir, "builtBy" to buildReceiptResource) }
