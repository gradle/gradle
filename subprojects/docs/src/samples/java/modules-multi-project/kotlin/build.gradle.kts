subprojects {
    version = "1.0.2"
    group = "org.gradle.sample"

    repositories {
        jcenter()
    }

    // tag::inferModulePath[]
    plugins.withType<JavaPlugin>().configureEach {
        configure<JavaPluginExtension> {
            modularity.inferModulePath.set(true)
        }
    }
    // end::inferModulePath[]

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
