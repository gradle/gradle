// tag::accessing-metadata-artifact[]
import groovy.xml.XmlSlurper

plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.guava:guava:18.0")
}

tasks.register("printGuavaMetadata") {
    dependsOn(configurations.compileClasspath)

    val query: ArtifactResolutionQuery = dependencies.createArtifactResolutionQuery()
        .forModule("com.google.guava", "guava", "18.0")
        .withArtifacts(MavenModule::class, MavenPomArtifact::class)
    val result: ArtifactResolutionResult = query.execute()

    val artifactPomFiles = result.resolvedComponents.map { component ->
        val mavenPomArtifacts: Set<ArtifactResult> = component.getArtifacts(MavenPomArtifact::class)
        val guavaPomArtifact =
            mavenPomArtifacts.find { it is ResolvedArtifactResult && it.file.name == "guava-18.0.pom" } as ResolvedArtifactResult
        guavaPomArtifact.file
    }

    doLast {
        artifactPomFiles.forEach { artifactFile ->
            val xml = XmlSlurper().parse(artifactFile)
            println(artifactFile.name)
            println(xml.getProperty("name"))
            println(xml.getProperty("description"))
        }
    }
}
// end::accessing-metadata-artifact[]
