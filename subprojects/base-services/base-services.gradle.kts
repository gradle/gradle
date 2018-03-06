/*
 * A set of generic services and utilities.
 *
 * Should have a very small set of dependencies, and should be appropriate to embed in an external
 * application (eg as part of the tooling API).
 */

import java.util.concurrent.Callable

plugins {
    `java-library`
    id("gradlebuild.classycle")
}

java {
    sourceCompatibility = sourceCompatibleVersion
}

dependencies {
    compile(project(":distributionsDependencies"))

    api(library("guava"))
    api(library("jsr305"))
    api(library("fastutil"))

    implementation(library("slf4j_api"))
    implementation(library("commons_lang"))
    implementation(library("commons_io"))
    implementation(library("jcip"))

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
        setProperty("include", listOf("HashingAlgorithmsBenchmark"))
    }
}

val buildReceiptPackage: String by rootProject.extra



val buildReceiptResource by tasks.creating(Copy::class) {
    from(Callable { tasks.getByPath(":createBuildReceipt").outputs.files })
    destinationDir = file("${gradlebuildJava.generatedTestResourcesDir}/$buildReceiptPackage")
}

java.sourceSets {
    "main" {
        output.dir(mapOf("builtBy" to buildReceiptResource), gradlebuildJava.generatedTestResourcesDir)
    }
}
