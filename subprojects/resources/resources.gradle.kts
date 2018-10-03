import org.gradle.gradlebuild.unittestandcompile.ModuleType

/*
 * A set of general-purpose resource abstractions.
 */
plugins {
    `java-library`
    id("gradlebuild.classycle")
}

dependencies {
    api(project(":baseServices"))
    api(project(":messaging"))
    api(project(":native"))

    implementation(library("guava"))
    implementation(library("commons_io"))

    integTestImplementation(project(":internalIntegTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.ENTRY_POINT
}

testFixtures {
    from(":core")
}
