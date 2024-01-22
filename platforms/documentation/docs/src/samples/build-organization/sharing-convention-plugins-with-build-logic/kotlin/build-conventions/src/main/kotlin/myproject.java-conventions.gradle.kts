plugins {
    id("java-library")
}

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}
