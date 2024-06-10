plugins {
    id("gradlebuild.api-extractor")
}

description = "Extract API classes from JVM classes."

dependencies {
    implementation(platform("gradlebuild:build-platform"))
    implementation("org.ow2.asm:asm-tree")
    implementation("com.google.guava:guava") {
        // Used for its nullability annotations, not needed at runtime
        exclude("org.checkerframework", "checker-qual")
        exclude("com.google.errorprone", "error_prone_annotations")
        exclude("com.google.guava", "listenablefuture")
    }
}
