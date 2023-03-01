plugins {
    id("myproject.java-library-conventions")
}

// tag::dependencies[]
dependencies {
    implementation(project(":lib"))
}
// end::dependencies[]

// tag::substitution_rule[]
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(platform(module("com.google.guava:guava:28.2-jre")))
            .using(module("com.google.guava:guava:28.2-jre"))
    }
}
// end::substitution_rule[]

// tag::substitution_rule_alternative[]
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(variant(module("com.google.guava:guava:28.2-jre")) {
            attributes {
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.REGULAR_PLATFORM))
            }
        }).using(module("com.google.guava:guava:28.2-jre"))
    }
}
// end::substitution_rule_alternative[]

tasks.register("resolve") {
    val classpath: FileCollection = configurations.runtimeClasspath.get()
    inputs.files(classpath)
    doLast {
        println(classpath.files.map { it.name })
    }
}
