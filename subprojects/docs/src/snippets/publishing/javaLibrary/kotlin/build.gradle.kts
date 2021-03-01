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
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}
// end::configure-publishing[]

// tag::configure-generate-task[]
tasks.withType<GenerateMavenPom>().configureEach {
    val matcher = Regex("""generatePomFileFor(\w+)Publication""").matchEntire(name)
    val publicationName = matcher?.let { it.groupValues[1] }
    destination = layout.buildDirectory.file("poms/${publicationName}-pom.xml").get().asFile
}
// end::configure-generate-task[]

// tag::enable-build-id[]
publishing {
    publications {
        create<MavenPublication>("main") {
            from(components["java"])
            withBuildIdentifier()
        }
    }
}
// end::enable-build-id[]

// tag::disable_validation[]
tasks.withType<GenerateModuleMetadata> {
    // The value 'enforced_platform' is provided in the validation
    // error message you got
    suppressedValidationErrors.add("enforced_platform")
}
// end::disable_validation[]
