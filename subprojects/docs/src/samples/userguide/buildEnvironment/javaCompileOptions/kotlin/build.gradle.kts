plugins {
    java
}

tasks.withType(JavaCompile::class) {
    options.compilerArgs = listOf("-Xdoclint:none", "-Xlint:none", "-nowarn")
}
