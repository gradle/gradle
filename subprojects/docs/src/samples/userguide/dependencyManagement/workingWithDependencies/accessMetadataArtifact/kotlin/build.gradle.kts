// tag::accessing-metadata-artifact[]
import groovy.util.XmlSlurper

plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.guava:guava:18.0")
}

tasks.create("printGuavaMetadata") {
    dependsOn(configurations.compileClasspath)

    doLast {
        val query: ArtifactResolutionQuery = dependencies.createArtifactResolutionQuery()
            .forModule("com.google.guava", "guava", "18.0")
            .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
        val result: ArtifactResolutionResult = query.execute()

        result.resolvedComponents.forEach { component ->
            val mavenPomArtifacts: Set<ArtifactResult> = component.getArtifacts(MavenPomArtifact::class.java)
            val guavaPomArtifact =
                mavenPomArtifacts.find { it is ResolvedArtifactResult && it.file.name == "guava-18.0.pom" } as ResolvedArtifactResult
            val xml = XmlSlurper().parse(guavaPomArtifact.file)
            // println(guavaPomArtifact.file)
            println(xml.getProperty("name"))
            println(xml.getProperty("description").toString().trimIndent().trim())
        }
    }
}
// end::accessing-metadata-artifact[]
