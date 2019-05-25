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
require(hasProperty("javaHome")) { "Set the property 'javaHome' in your your gradle.properties pointing to a Java 6 or 7 installation" }
require(hasProperty("targetJavaVersion")) { "Set the property 'targetJavaVersion' in your your gradle.properties to '1.6' or '1.7'" }

val javaHome: String by project
val targetJavaVersion: String by project

java {
    sourceCompatibility = JavaVersion.toVersion(targetJavaVersion)
}

val javaExecutablesPath = File(javaHome, "bin")
fun javaExecutable(execName: String): String {
    val executable = File(javaExecutablesPath, execName)
    require(executable.exists()) { "There is no ${execName} executable in ${javaExecutablesPath}" }
    return executable.toString()
}
tasks.withType<JavaCompile>().configureEach {
    options.apply {
        isFork = true
        forkOptions.javaHome = file(javaHome)
    }
}
tasks.withType<Javadoc>().configureEach {
    executable = javaExecutable("javadoc")
}
tasks.withType<Test>().configureEach {
    executable = javaExecutable("java")
}
tasks.withType<JavaExec>().configureEach {
    executable = javaExecutable("java")
}
// end::java-cross-compilation[]

tasks.withType<Test>().configureEach {
    systemProperty("targetJavaVersion", targetJavaVersion)
}

tasks.register("checkJavadocOutput") {
    dependsOn(tasks.javadoc)
    doLast {
        require(File(the<JavaPluginConvention>().docsDir, "javadoc/org/gradle/Person.html").readText().contains("<p>Represents a person.</p>"))
    }
}

tasks.build { dependsOn("checkJavadocOutput") }

