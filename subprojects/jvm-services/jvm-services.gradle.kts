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
    testImplementation(project(":files"))
    testImplementation(project(":resources"))
    testImplementation(library("slf4j_api"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":core")
}
