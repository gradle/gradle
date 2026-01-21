// tag::use-java-gradle-plugin-plugin[]
plugins {
    `java-gradle-plugin`
}
// end::use-java-gradle-plugin-plugin[]

// tag::gradle-plugin-block[]
gradlePlugin {
    plugins {
        register("org.gradle.sample.simple-plugin") {
            implementationClass = "org.gradle.sample.SimplePlugin"
        }
    }
}
// end::gradle-plugin-block[]
