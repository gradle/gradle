// tag::all[]
subprojects {
    apply(plugin = "java")
    apply(plugin = "ivy-publish")

    version = "1.0"
    group = "org.gradle.sample"

    repositories {
        mavenCentral()
    }
    // tag::publish-custom-artifact[]
    task<Jar>("sourcesJar") {
        from(project.the<SourceSetContainer>()["main"].java)
        classifier = "sources"
    }
    // end::publish-custom-artifact[]
}

project(":project1") {
    description = "The first project"

    dependencies {
        "implementation"("junit:junit:4.12")
        "implementation"(project(":project2"))
    }
}

project(":project2") {
    description = "The second project"

    dependencies {
        "implementation"("commons-collections:commons-collections:3.2.2")
    }
}

subprojects {
    // tag::publish-custom-artifact[]
    configure<PublishingExtension>() {
        // end::publish-custom-artifact[]
        repositories {
            ivy {
                // change to point to your repo, e.g. http://my.org/repo
                url = uri("${rootProject.buildDir}/repo")
            }
        }
// tag::publish-custom-artifact[]
        publications {
            create<IvyPublication>("ivy") {
                from(components["java"])
                artifact(tasks["sourcesJar"]) {
                    type = "sources"
                    conf = "compile"
                }
// end::publish-custom-artifact[]
                descriptor.description {
                    text.set(description)
                }
// tag::publish-custom-artifact[]
            }
        }
    }
// end::publish-custom-artifact[]
}
// end::all[]
