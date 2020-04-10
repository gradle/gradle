// tag::apply-plugins[]
plugins {
    `java-library`
    `maven-publish`
}
// end::apply-plugins[]

repositories {
    mavenCentral()
}

dependencies {
    api("commons-httpclient:commons-httpclient:3.1")
    implementation("org.apache.commons:commons-lang3:3.5")
}

// tag::configure-publishing[]
group = "org.example"
version = "1.0"

publishing {
    publications {
        create<MavenPublication>("myLibrary") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "myRepo"
            url = uri("file://${buildDir}/repo")
        }
    }
}
// end::configure-publishing[]

// tag::configure-generate-task[]
tasks.withType<GenerateMavenPom>().configureEach {
    val matcher = Regex("""generatePomFileFor(\w+)Publication""").matchEntire(name)
    val publicationName = matcher?.let { it.groupValues[1] }
    destination = file("$buildDir/poms/$publicationName-pom.xml")
}
// end::configure-generate-task[]
