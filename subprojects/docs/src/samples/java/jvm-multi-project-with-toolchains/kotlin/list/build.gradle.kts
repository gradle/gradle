plugins {
    id("myproject.java-conventions")
    `java-library`
}

// tag::customToolchain[]
tasks.withType<JavaCompile>().configureEach {
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(8))
    })
}

tasks.register<Test>("testsOn17") {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    })
}
// end::customToolchain[]
