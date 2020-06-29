dependencies {
    implementation(project(":basics"))
    implementation(project(":dependencyModules"))
    implementation(project(":moduleIdentity"))

    implementation("org.eclipse.jgit:org.eclipse.jgit")
    implementation("org.jsoup:jsoup")
    implementation("com.google.guava:guava")
    implementation("org.ow2.asm:asm")
    implementation("org.ow2.asm:asm-commons")
    implementation("com.google.code.gson:gson")
    implementation("org.gradle:test-retry-gradle-plugin")

    implementation("com.thoughtworks.qdox:qdox") {
        because("ParameterNamesIndex")
    }
}
