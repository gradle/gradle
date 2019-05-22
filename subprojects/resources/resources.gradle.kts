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
    implementation(project(":messaging"))
    implementation(project(":native"))

    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_io"))

    testImplementation(project(":processServices"))
    testImplementation(project(":coreApi"))
    testImplementation(project(":files"))

    integTestImplementation(project(":internalIntegTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
}
