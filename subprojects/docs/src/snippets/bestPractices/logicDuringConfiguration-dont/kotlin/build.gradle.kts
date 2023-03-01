plugins {
    `java-library`
}

repositories {
    mavenCentral()
}


configurations.all {
    incoming.beforeResolve {
        throw IllegalStateException("You shouldn't resolve configurations during configuration phase!")
    }
}
// tag::config-logic[]
dependencies {
    implementation("log4j:log4j:1.2.17")
}

tasks.register("printArtifactNames") {
    // always executed
    val libraryNames = configurations.compileClasspath.get().map { it.name }

    doLast {
        logger.quiet(libraryNames.joinToString())
    }
}
// end::config-logic[]
