import org.gradle.gradlebuild.unittestandcompile.ModuleType

/**
 * Process execution abstractions.
 */
plugins {
    `java-library`
}

dependencies {
    implementation(project(":baseServices"))

    implementation(project(":messaging"))
    implementation(project(":native"))

    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("nativePlatform"))

    testImplementation(testFixtures(project(":core")))

    integTestRuntimeOnly(project(":runtimeApiInfo"))
}

gradlebuildJava {
    moduleType = ModuleType.WORKER
}

