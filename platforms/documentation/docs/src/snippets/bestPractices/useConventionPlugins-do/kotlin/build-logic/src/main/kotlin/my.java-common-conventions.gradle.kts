// tag::do-this[]
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation")) // <3>
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1 // <4>
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3") // <5>
}
// end::do-this[]

val conventionPluginApplied = tasks.register("conventionPluginApplied")
tasks.compileJava.configure { dependsOn(conventionPluginApplied) }
