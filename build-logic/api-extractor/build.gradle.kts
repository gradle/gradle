plugins {
    id("gradlebuild.api-extractor")
}

description = "Extract API classes from JVM classes."

dependencies {
    // TODO These should look a lot simpler.
    //      We should probably reuse them from build-logic-commons:build-platform,
    //      but doing so causes a lot of Kotlin tests like ManagedPropertyJavaInterOpIntegrationTest fail
    //      with an error about a missing method:
    //      kotlinx.metadata.jvm.KotlinClassMetadata kotlinx.metadata.jvm.KotlinClassMetadata$Companion.read(kotlinx.metadata.jvm.KotlinClassHeader)
    implementation("org.ow2.asm:asm:9.7")
    implementation("com.google.guava:guava:32.1.2-jre") {
        exclude("org.checkerframework", "checker-qual")
        exclude("com.google.errorprone", "error_prone_annotations")
        exclude("com.google.guava", "listenablefuture")
    }
}
