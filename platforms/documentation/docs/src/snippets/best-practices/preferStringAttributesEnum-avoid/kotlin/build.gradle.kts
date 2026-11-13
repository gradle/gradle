import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.publish.tasks.GenerateModuleMetadata

plugins {
    `java-library`
    `maven-publish`
}

group = "com.example"
version = "1.0"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

// tag::avoid-this[]
enum class MyFormat(val extension: String) : Named { // <1>
    TYPE_A("txt-a"),
    TYPE_B("txt-b");

    override fun getName(): String = name // <2>
}

val MY_FORMAT = Attribute.of("my-format", MyFormat::class.java)
dependencies.attributesSchema.attribute(MY_FORMAT)

configurations.named("apiElements") {
    attributes {
        attribute(MY_FORMAT, MyFormat.TYPE_A) // <3>
    }
}

val generateMetadata =
    tasks.named<GenerateModuleMetadata>("generateMetadataFileForMavenPublication")

tasks.register("showPublishedFormat") {
    val moduleFile = generateMetadata.flatMap { it.outputFile }
    inputs.file(moduleFile)
    val inBuild = MyFormat.TYPE_A.extension // <4>
    doLast {
        logger.lifecycle("in this build:  MyFormat.TYPE_A.extension = $inBuild")
        moduleFile.get().asFile.readLines()
            .filter { it.contains("my-format") }
            .forEach { logger.lifecycle("in module.json: ${it.trim().removeSuffix(",")}") } // <5>
    }
}
// end::avoid-this[]
