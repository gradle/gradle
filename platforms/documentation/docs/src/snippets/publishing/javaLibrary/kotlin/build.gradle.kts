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

// tag::enable-build-id[]
publishing {
    publications {
        create<MavenPublication>("myLibrary") {
            from(components["java"])
// end::configure-publishing[]
            withBuildIdentifier()
// tag::configure-publishing[]
        }
    }
// end::enable-build-id[]

    repositories {
        maven {
            name = "myRepo"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
// tag::enable-build-id[]
}
// end::configure-publishing[]
// end::enable-build-id[]

// tag::configure-generate-task[]
tasks.withType<GenerateMavenPom>().configureEach {
    val matcher = Regex("""generatePomFileFor(\w+)Publication""").matchEntire(name)
    val publicationName = matcher?.let { it.groupValues[1] }
    destination = layout.buildDirectory.file("poms/${publicationName}-pom.xml").get().asFile
}
// end::configure-generate-task[]

dependencies {
    implementation(enforcedPlatform("org.junit:junit-bom:5.7.1"))
}

// tag::disable_validation[]
tasks.withType<GenerateModuleMetadata> {
    // The value 'enforced-platform' is provided in the validation
    // error message you got
    suppressedValidationErrors.add("enforced-platform")
}
// end::disable_validation[]
