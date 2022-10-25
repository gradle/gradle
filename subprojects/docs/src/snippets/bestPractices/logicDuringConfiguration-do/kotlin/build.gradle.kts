plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::config-logic[]
dependencies {
    implementation("log4j:log4j:1.2.17")
}

tasks.register("printArtifactNames") {
    val compileClasspath: FileCollection = configurations.compileClasspath.get()
    doLast {
        val libraryNames = compileClasspath.map { it.name }
        logger.quiet(libraryNames.joinToString())
    }
}
// end::config-logic[]
