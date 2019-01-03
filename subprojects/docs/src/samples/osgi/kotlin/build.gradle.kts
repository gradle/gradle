// tag::use-plugin[]
plugins {
// end::use-plugin[]
    groovy
// tag::use-plugin[]
    osgi
}
// end::use-plugin[]

group = "gradle_tooling"
version = "1.0"

repositories {
    mavenCentral()
    maven {
        url = uri("https://repository.jboss.org/maven2/")
    }
}

dependencies {
    compile("org.codehaus.groovy:groovy:2.4.15")
    compile("org.eclipse:osgi:3.5.0.v20090520")
}

tasks.withType<Jar>().configureEach {
    (manifest as? OsgiManifest)?.apply {
        version = "1.0.0"
        name = "Example Gradle Activator"
        instruction("Bundle-Activator", "org.gradle.GradleActivator")
        instruction("Import-Package", "*")
        instruction("Export-Package", "*")
        attributes(mapOf("Built-By" to gradle.gradleVersion))
    }
}
