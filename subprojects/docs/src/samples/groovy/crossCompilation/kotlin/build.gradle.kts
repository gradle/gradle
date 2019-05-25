plugins {
    groovy
}
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.codehaus.groovy:groovy-all:2.4.15")
    testImplementation("junit:junit:4.12")
}

// tag::groovy-cross-compilation[]
java {
    sourceCompatibility = JavaVersion.VERSION_1_6
    targetCompatibility = JavaVersion.VERSION_1_6
}

require(hasProperty("java6Home")) { "Set the property 'java6Home' in your your gradle.properties pointing to a Java 6 installation" }
val java6Home: String by project
val javaExecutablesPath = File(java6Home, "bin")
fun javaExecutable(execName: String): String {
    val executable = File(javaExecutablesPath, execName)
    require(executable.exists()) { "There is no $execName executable in $javaExecutablesPath" }
    return executable.toString()
}
tasks.withType<JavaCompile>().configureEach {
    options.apply {
        isFork = true
        forkOptions.javaHome = file(java6Home)
    }
}
tasks.withType<Javadoc>().configureEach {
    executable = javaExecutable("javadoc")
}
tasks.withType<Test>().configureEach {
    executable = javaExecutable("java")
}
tasks.withType<JavaExec>.configureEach {
    executable = javaExecutable("java")
}
// end::groovy-cross-compilation[]

