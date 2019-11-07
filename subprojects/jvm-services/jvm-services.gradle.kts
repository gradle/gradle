import org.gradle.gradlebuild.unittestandcompile.ModuleType

/**
 * JVM invocation and inspection abstractions.
 */
plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":processServices"))

    testImplementation(project(":native"))
    testImplementation(project(":coreApi"))
    testImplementation(project(":fileCollections"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":resources"))
    testImplementation(testFixtures(project(":core")))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

