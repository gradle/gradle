/*
 * A set of generic services and utilities.
 *
 * Should have a very small set of dependencies, and should be appropriate to embed in an external
 * application (eg as part of the tooling API).
 */

import java.util.concurrent.Callable

plugins {
    `java-library`
    id("classycle")
}

val javaVersion: JavaVersion by rootProject.extra

java {
    sourceCompatibility =
        if (javaVersion.isJava9Compatible) JavaVersion.VERSION_1_6
        else JavaVersion.VERSION_1_5
}

dependencies {
    compile(project(":distributionsDependencies"))

    api(Libraries.guava.coordinates)
    api(Libraries.jsr305.coordinates)
    api(Libraries.fastutil.coordinates)

    implementation(Libraries.slf4jApi.coordinates)
    implementation(Libraries.commonsLang.coordinates)
    implementation(Libraries.commonsIo.coordinates)
    implementation(Libraries.jcip.coordinates)

    jmh(Libraries.bouncycastleProvider.coordinates) {
        version {
            prefer(Libraries.bouncycastleProvider.version)
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

val generatedTestResourcesDir: File by extra

val buildReceiptPackage: String by rootProject.extra

val buildReceiptResource by tasks.creating(Copy::class) {
    from(Callable { tasks.getByPath(":createBuildReceipt").outputs.files })
    destinationDir = file("$generatedTestResourcesDir/$buildReceiptPackage")
}

java.sourceSets {
    "main" {
        output.dir(mapOf("builtBy" to buildReceiptResource), generatedTestResourcesDir)
    }
}
