plugins {
    id("gradlebuild.java-shared-runtime")
    id("gradlebuild.publish-public-libraries")
}

description = "Logic to extract API classes from JVM classes that is shared between build-logic and runtime."

dependencies {
    // TODO These should not need version numbers specified here.
    //      We should probably reuse them from build-logic-commons:build-platform,
    //      but then we have an incompatibility between kotlinx-metadata versions.
    //      The error can be reproduced in production code by running an integration test
    //      that uses Kotlin DSL, like ManagedPropertyJavaInterOpIntegrationTest.
    implementation("org.ow2.asm:asm:9.7")
    implementation("com.google.guava:guava:32.1.2-jre") {
        isTransitive = false
    }
}
