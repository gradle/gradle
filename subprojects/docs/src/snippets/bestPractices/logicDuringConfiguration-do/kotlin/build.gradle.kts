plugins {
    `java-library`
}

repositories {
    jcenter()
}

// tag::config-logic[]
dependencies {
    implementation("log4j:log4j:1.2.17")
}

tasks.register("printArtifactNames") {
    doLast {
        val libraryNames = configurations.compileClasspath.get().map { it.name }
        logger.quiet(libraryNames.toString())
    }
}
// end::config-logic[]
