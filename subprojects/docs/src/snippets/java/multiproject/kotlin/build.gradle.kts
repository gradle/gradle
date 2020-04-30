// tag::configuration-injection[]
subprojects {
    apply(plugin = "java")
    apply(plugin = "eclipse-wtp")

    repositories {
        mavenCentral()
    }

    dependencies {
        "testImplementation"("junit:junit:4.13")
    }

    version = "1.0"

    tasks.named<Jar>("jar") {
        manifest.attributes("provider" to "gradle")
    }
}
// end::configuration-injection[]
