import org.gradle.gradlebuild.unittestandcompile.ModuleType

/**
 * JVM invocation and inspection abstractions.
 */
plugins {
    `java-library`
    id("gradlebuild.classycle")
}

dependencies {
    api(project(":baseServices"))
    api(project(":processServices"))
}

gradlebuildJava {
    moduleType = ModuleType.ENTRY_POINT
}

testFixtures {
    from(":core")
}
