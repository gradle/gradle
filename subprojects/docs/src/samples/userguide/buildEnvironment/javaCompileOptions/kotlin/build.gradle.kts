plugins {
    java
}

tasks.withType<JavaCompile> {
    options.compilerArgs = listOf("-Xdoclint:none", "-Xlint:none", "-nowarn")
}
