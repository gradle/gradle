// tag::avoid-this[]
plugins {
    id("java-library")
    id("maven-publish")
}

tasks.named<JavaCompile>("compileJava").configure { // <1>
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

tasks.named<GenerateMavenPom>("generatePomFileForMavenPublication").configure { // <2>
    pom.url = "sample.gradle.org"
}
// end::avoid-this[]

version = "1.0.0"
group = "org.gradle.sample"
