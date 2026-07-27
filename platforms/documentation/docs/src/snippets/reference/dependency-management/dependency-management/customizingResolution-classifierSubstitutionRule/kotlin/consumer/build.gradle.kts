plugins {
    id("myproject.java-library-conventions")
}

// tag::dependencies[]
dependencies {
    implementation("com.google.guava:guava:28.2-jre")
    implementation("co.paralleluniverse:quasar-core:0.8.0")
    implementation(project(":lib"))
}
// end::dependencies[]

// tag::substitution_rule[]
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("co.paralleluniverse:quasar-core"))
            .using(module("co.paralleluniverse:quasar-core:0.8.0"))
            .withoutClassifier()
    }
}
// end::substitution_rule[]

tasks.register("resolve") {
    val classpath: FileCollection = configurations.runtimeClasspath.get()
    inputs.files(classpath)
    doLast {
        println(classpath.files.map { it.name })
    }
}
