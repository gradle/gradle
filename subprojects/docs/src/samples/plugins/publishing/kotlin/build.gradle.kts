// tag::complete-plugin-publishing[]
plugins {
    `java-gradle-plugin`
    `maven-publish`
    `ivy-publish`
}

group = "org.gradle.sample"
version = "1.0.0"

gradlePlugin {
    plugins {
        create("hello") {
            id = "org.gradle.sample.hello"
            implementationClass = "org.gradle.sample.hello.HelloPlugin"
        }
        create("goodbye") {
            id = "org.gradle.sample.goodbye"
            implementationClass = "org.gradle.sample.goodbye.GoodbyePlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            url = uri("../../consuming/maven-repo")
        }
        ivy {
            url = uri("../../consuming/ivy-repo")
        }
    }
}
// end::complete-plugin-publishing[]
