plugins {
    scala
}
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.scala-lang:scala-library:2.11.12")
    testImplementation("org.scalatest:scalatest_2.11:3.0.0")
    testImplementation("junit:junit:4.12")
}

// tag::scala-cross-compilation[]
java {
    sourceCompatibility = JavaVersion.VERSION_1_6
}

require(hasProperty("java6Home")) { "Set the property 'java6Home' in your your gradle.properties pointing to a Java 6 installation" }
val java6Home: String by project
val javaExecutablesPath = File(java6Home, "bin")
fun javaExecutable(execName: String): String {
    val executable = File(javaExecutablesPath, execName)
    require(executable.exists()) { "There is no ${execName} executable in ${javaExecutablesPath}" }
    return executable.toString()
}

tasks.withType<ScalaCompile>().configureEach {
    options.apply {
        isFork = true
        forkOptions.javaHome = file(java6Home)
    }
}
tasks.withType<Test>().configureEach {
    executable = javaExecutable("java")
}
tasks.withType<JavaExec>().configureEach {
    executable = javaExecutable("java")
}
tasks.withType<Javadoc>().configureEach {
    executable = javaExecutable("javadoc")
}
// end::scala-cross-compilation[]
