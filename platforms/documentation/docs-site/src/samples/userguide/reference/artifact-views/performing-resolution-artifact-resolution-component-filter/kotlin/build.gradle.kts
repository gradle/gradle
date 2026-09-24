plugins {
    id("java-library")
}

repositories {
    mavenCentral()
}

// tag::component-filter-dependencies[]
dependencies {
    implementation(project(":other"))
    implementation("org.junit.jupiter:junit-jupiter-api:5.11.0")
}
// end::component-filter-dependencies[]

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

// tag::resolve-component-filter[]
tasks.register<ResolveFiles>("resolveProjects") {
    files.from(configurations.runtimeClasspath.map {
        it.incoming.artifactView {
            componentFilter {
                it is ProjectComponentIdentifier
            }
        }.files
    })
}
tasks.register<ResolveFiles>("resolveModules") {
// end::resolve-component-filter[]
    dependsOn(tasks.named("resolveProjects")) // To preserve output ordering
// tag::resolve-component-filter[]
    files.from(configurations.runtimeClasspath.map {
        it.incoming.artifactView {
            componentFilter {
                it is ModuleComponentIdentifier
            }
        }.files
    })
}
// end::resolve-component-filter[]
