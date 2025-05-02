// Define Java conventions for this organization.
// Projects need to use the Java, Checkstyle and Spotbugs plugins.

// tag::apply-external-plugin[]
plugins {
    java
    checkstyle

    // NOTE: external plugin version is specified in implementation dependency artifact of the project's build file
    id("com.github.spotbugs")
}
// end::apply-external-plugin[]

// Projects should use Maven Central for external dependencies
// This could be the organization's private repository
repositories {
    mavenCentral()
}

// Use the Checkstyle rules provided by the convention plugin
// Do not allow any warnings
checkstyle {
    config = resources.text.fromString(com.example.CheckstyleUtil.getCheckstyleConfig("/checkstyle.xml"))
    maxWarnings = 0
}

// Enable deprecation messages when compiling Java code
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}
