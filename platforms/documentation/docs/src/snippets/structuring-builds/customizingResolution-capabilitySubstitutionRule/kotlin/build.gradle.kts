plugins {
    `java-library`
}

dependencies {
    implementation("com.acme:lib:1.0")
}

// tag::substitution_rule[]
configurations.testCompileClasspath {
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.acme:lib:1.0")).using(variant(module("com.acme:lib:1.0")) {
            capabilities {
                requireCapability("com.acme:lib-test-fixtures")
            }
        })
    }
}
// end::substitution_rule[]


tasks.register("resolve") {
    inputs.files(configurations.testCompileClasspath)

    val files = configurations.testCompileClasspath.get().files
    doLast {
        println(files.map { it.name })
    }
}
