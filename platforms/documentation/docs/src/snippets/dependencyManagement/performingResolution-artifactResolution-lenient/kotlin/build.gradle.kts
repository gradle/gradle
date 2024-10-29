plugins {
    id("java-library")
}

repositories {
    mavenCentral()
}

// tag::lenient-resolution-dependencies[]
dependencies {
    implementation("does:not:exist")
    implementation("org.junit.jupiter:junit-jupiter-api:5.11.0")
}
// end::lenient-resolution-dependencies[]

abstract class ResolveFiles : DefaultTask() {

    @get:InputFiles
    abstract val files: ConfigurableFileCollection

    @TaskAction
    fun print() {
        files.forEach {
            println(it.name)
        }
    }
}

// tag::resolve-lenient[]
tasks.register<ResolveFiles>("resolveLenient") {
    files.from(configurations.runtimeClasspath.map {
        it.incoming.artifactView {
            isLenient = true
        }.files
    })
}
// end::resolve-lenient[]
