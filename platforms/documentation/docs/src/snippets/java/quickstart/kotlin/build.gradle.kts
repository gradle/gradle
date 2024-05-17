// tag::complete-example[]
// tag::use-plugin[]
// tag::use-eclipse-plugin[]
plugins {
    // end::use-eclipse-plugin[]
// end::use-plugin[]
// tag::use-plugin[]
    java
// end::use-plugin[]
// tag::use-eclipse-plugin[]
    eclipse
// end::use-eclipse-plugin[]
// tag::use-plugin[]
// tag::use-eclipse-plugin[]
}
// end::use-eclipse-plugin[]
// end::use-plugin[]
// end::complete-example[]


// tag::use-plugin-legacy[]
apply(plugin = "java")
// end::use-plugin-legacy[]

// tag::complete-example[]
// tag::customization[]
version = "1.0"
tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "Gradle Quickstart",
            "Implementation-Version" to archiveVersion)
    }
}
// end::customization[]

// tag::repo[]
repositories {
    mavenCentral()
}
// end::repo[]

// tag::dependencies[]
dependencies {
    implementation(group = "commons-collections", name = "commons-collections", version = "3.2.2")
    testImplementation(group = "junit", name = "junit", version = "4.+")
}
// end::dependencies[]

// tag::task-customization[]
tasks.test {
    systemProperties("property" to "value")
}
// end::task-customization[]
// end::complete-example[]
