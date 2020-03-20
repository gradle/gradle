// tag::use-and-configure-plugin[]
plugins {
    `java-gradle-plugin`
// end::use-and-configure-plugin[]
    `maven-publish`
// tag::use-and-configure-plugin[]
}

// end::use-and-configure-plugin[]
group = "org.gradle"
version = "1.0-SNAPSHOT"

// tag::use-and-configure-plugin[]
gradlePlugin {
    plugins {
        create("simplePlugin") {
            id = "org.samples.greeting"
            implementationClass = "org.gradle.GreetingPlugin"
        }
    }
}
// end::use-and-configure-plugin[]

publishing {
    repositories {
        maven {
            url = uri("$buildDir/repo")
        }
    }
}
