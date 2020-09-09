plugins {
    id("java")
}

repositories {
    jcenter()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}
