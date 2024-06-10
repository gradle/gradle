plugins {
    id("gradlebuild.api-extractor")
}

description = "Extract API classes from JVM classes."

dependencies {
    implementation(platform("gradlebuild:build-platform"))
    implementation("org.ow2.asm:asm-tree")
    implementation("com.google.guava:guava")
}
