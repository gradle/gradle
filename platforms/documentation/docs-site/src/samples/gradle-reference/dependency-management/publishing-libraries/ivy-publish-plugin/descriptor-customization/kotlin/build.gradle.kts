plugins {
    `ivy-publish`
}

group = "org.gradle.sample"
version = "1.0"

publishing {
    publications {
        create<IvyPublication>("ivyCustom") {
            descriptor {
                license {
                    name = "The Apache License, Version 2.0"
                    url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                }
                author {
                    name = "Jane Doe"
                    url = "http://example.com/users/jane"
                }
                description {
                    text = "A concise description of my library"
                    homepage = "http://www.example.com/library"
                }
            }
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
        }
    }
    repositories {
        ivy {
            // change to point to your repo, e.g. http://my.org/repo
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

tasks.named<GenerateIvyDescriptor>("generateDescriptorFileForIvyCustomPublication") {
    destination = layout.buildDirectory.file("generated-ivy.xml").get().asFile
}
