// tag::avoid-this[]
plugins {
    id("java-library")
    id("maven-publish")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

tasks.named<GenerateMavenPom>("generatePomFileForMavenPublication").configure { // <1>
    pom.url = "sample.gradle.org"
}
// end::avoid-this[]

version = "1.0.0"
group = "org.gradle.sample"
