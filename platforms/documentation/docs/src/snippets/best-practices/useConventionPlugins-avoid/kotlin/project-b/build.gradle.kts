// tag::avoid-this[]
plugins {
    `java-library`
}

// Duplicated configuration across multiple build files
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation")) // <1>
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1 // <2>
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3") // <3>
    api("com.google.guava:guava:23.0") // <4>
}
// end::avoid-this[]
