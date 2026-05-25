// tag::plugins[]
plugins {
    `java-library`
    `maven-publish`
}
// ...
// end::plugins[]

repositories {
    mavenCentral()
}

group = "org.gradle.demo"
version = "1.0"

// tag::register_variant[]
sourceSets {
    create("mongodbSupport") {
        java {
            srcDir("src/mongodb/java")
        }
    }
}

// tag::register_variant_extra_jars[]
java {
    registerFeature("mongodbSupport") {
        usingSourceSet(sourceSets["mongodbSupport"])
// end::register_variant[]
        withJavadocJar()
        withSourcesJar()
// tag::register_variant2[]
    }
}
// end::register_variant2[]
// end::register_variant_extra_jars[]

// tag::variant_dependencies[]
dependencies {
    "mongodbSupportImplementation"("org.mongodb:mongodb-driver-sync:3.9.1")
}
// end::variant_dependencies[]

// tag::publishing[]
publishing {
    publications {
        create("myLibrary", MavenPublication::class.java) {
            from(components["java"])
        }
    }
}
// end::publishing[]

