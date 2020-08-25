subprojects {
    apply(plugin = "groovy")
    repositories {
        jcenter()
    }
    dependencies {
        "implementation"(localGroovy())
    }
}

project(":astTransformationConsumer") {
// tag::groovy-compilation-avoidance[]
    val astTransformation by configurations.creating
    dependencies {
        astTransformation(project(":astTransformation"))
    }
    tasks.withType<GroovyCompile>().configureEach {
        astTransformationClasspath.from(astTransformation)
    }
// end::groovy-compilation-avoidance[]
}
