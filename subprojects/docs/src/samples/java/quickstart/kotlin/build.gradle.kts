// tag::use-plugin[]
apply(plugin = "java")
// end::use-plugin[]
// tag::use-eclipse-plugin[]
apply(plugin = "eclipse")
// end::use-eclipse-plugin[]

// tag::customization[]
version = "1.0"
tasks.getByName<Jar>("jar") {
    manifest {
        attributes(
            "Implementation-Title" to "Gradle Quickstart",
            "Implementation-Version" to version)
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
    "compile"(group = "commons-collections", name = "commons-collections", version = "3.2.2")
    "testCompile"(group = "junit", name = "junit", version = "4.+")
}
// end::dependencies[]

// tag::task-customization[]
tasks.getByName<Test>("test") {
    systemProperties("property" to "value")
}
// end::task-customization[]

// tag::upload[]
tasks.getByName<Upload>("uploadArchives") {
    repositories {
        flatDir {
            dirs("repos")
        }
    }
}
// end::upload[]
