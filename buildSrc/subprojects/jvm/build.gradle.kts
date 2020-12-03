dependencies {
    implementation(project(":basics"))
    implementation(project(":dependency-modules"))
    implementation(project(":module-identity"))

    implementation("org.eclipse.jgit:org.eclipse.jgit")
    implementation("org.jsoup:jsoup")
    implementation("com.google.guava:guava")
    implementation("org.ow2.asm:asm")
    implementation("org.ow2.asm:asm-commons")
    implementation("com.google.code.gson:gson")
    implementation("org.gradle:test-retry-gradle-plugin")
    implementation("com.gradle.enterprise:test-distribution-gradle-plugin:1.2.2-rc-1")

    implementation("com.thoughtworks.qdox:qdox") {
        because("ParameterNamesIndex")
    }
}
