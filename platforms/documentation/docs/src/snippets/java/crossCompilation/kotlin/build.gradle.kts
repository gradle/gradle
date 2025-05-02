plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    // We should use a legacy version to support running on jdk6
    implementation("commons-lang:commons-lang:2.6")
    testImplementation("junit:junit:4.+")
}

// tag::java-cross-compilation[]
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(7)
    }
}
// end::java-cross-compilation[]

tasks.withType<Test>().configureEach {
    project.findProperty("targetJavaVersion")?.let { systemProperty("targetJavaVersion", it) }
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(8) // Only compilation is supported < 8. Tests are not.
    }
}

tasks.register("checkJavadocOutput") {
    dependsOn(tasks.javadoc)
    val docsDir = java.docsDir
    doLast {
        require(File(docsDir.get().asFile, "javadoc/org/gradle/Person.html").readText().contains("<p>Represents a person.</p>"))
    }
}

tasks.build { dependsOn("checkJavadocOutput") }

