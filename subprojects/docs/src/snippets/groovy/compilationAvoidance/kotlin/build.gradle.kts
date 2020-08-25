subprojects {
    apply(plugin = "groovy")
    repositories {
        jcenter()
    }
    dependencies {
        "implementation"(localGroovy())
    }
}

project(":ast-transformation-consumer") {
// tag::groovy-compilation-avoidance[]
    val astTransformation by configurations.creating
    dependencies {
        astTransformation(project(":ast-transformation"))
    }
    tasks.withType<GroovyCompile>().configureEach {
        astTransformationClasspath.from(astTransformation)
    }
// end::groovy-compilation-avoidance[]
}
