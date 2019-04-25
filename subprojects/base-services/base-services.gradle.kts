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
    moduleType = ModuleType.ENTRY_POINT
}

dependencies {
    api(project(":distributionsDependencies"))

    api(library("guava"))
    api(library("jsr305"))
    api(library("fastutil"))

    implementation(library("slf4j_api"))
    implementation(library("commons_lang"))
    implementation(library("commons_io"))
    implementation(library("asm"))

    jmh(library("bouncycastle_provider")) {
        version {
            prefer(libraryVersion("bouncycastle_provider"))
        }
    }
}

testFixtures {
    from(":core")
}

jmh {
    withGroovyBuilder {
        setProperty("include", listOf("HashingBenchmark"/*, "HashingAlgorithmsBenchmark", "MessageDigestHasherBenchmark", "MessageDigestThreadingBenchmark" */))
    }
    profilers = listOf("hs_gc"/*, "stack"*/)
    // jvmArgs = listOf("-Xmx4G", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC")
    // forceGC = true // no-op for epsilon
    failOnError = true
    resultFormat = "JSON"
}

val buildReceiptPackage: String by rootProject.extra

val buildReceiptResource = tasks.register<Copy>("buildReceiptResource") {
    from(Callable { tasks.getByPath(":createBuildReceipt").outputs.files })
    destinationDir = file("${gradlebuildJava.generatedResourcesDir}/$buildReceiptPackage")
}

sourceSets.main { output.dir(gradlebuildJava.generatedResourcesDir, "builtBy" to buildReceiptResource) }
