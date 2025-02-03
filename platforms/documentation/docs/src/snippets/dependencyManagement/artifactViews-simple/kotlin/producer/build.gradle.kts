plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {

}

// Define a task that produces a custom artifact
tasks.register("createProductionArtifact", Jar::class) {
    archiveBaseName.set("production")
    from(sourceSets["main"].output)
    destinationDirectory.set(file("build/libs"))
}

configurations {
    // Define a custom configuration and extend from runtimeClasspath
    create("apiProductionElements") {
        extendsFrom(configurations.apiElements.get())
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named("production"))
        }
        artifacts {
            add("apiProductionElements", tasks.named("createProductionArtifact"))
        }
    }
}

tasks.register("checkProducerVariants") {
    val producerProject = project(":producer")

    // Check the outgoing variants for the producer
    producerProject.configurations.forEach { config ->
        println("Configuration: ${config.name}")
        config.outgoing.artifacts.forEach {
            println("  - Artifact: ${it.file}")
        }
    }
}

tasks.register("checkProducerAttributes") {
    configurations.forEach { config ->
        println("")
        println("Configuration: ${config.name}")
        println("Attributes:")
        config.attributes.keySet().forEach { key ->
            println("  - ${key.name} -> ${config.attributes.getAttribute(key)}")
        }
        println("Artifacts:")
        config.artifacts.forEach {
            println("${it.file}")
        }
    }
}
