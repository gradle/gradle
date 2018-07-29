import org.gradle.gradlebuild.unittestandcompile.ModuleType

/**
 * Process execution abstractions.
 */
plugins {
    id("java-library")
}

dependencies {
    api(project(":baseServices"))

    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(library("guava"))
    implementation(library("slf4j_api"))
}


gradlebuildJava {
    moduleType = ModuleType.ENTRY_POINT
}

testFixtures {
    from(":core")
}
