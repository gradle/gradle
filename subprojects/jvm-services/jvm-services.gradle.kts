
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

testFixtures {
    from(":core")
}
