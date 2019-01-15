plugins {
    `ivy-publish`
}

group = "org.gradle.sample"
version = "1.0"

publishing {
    // tag::customize-descriptor[]
    publications {
        create<IvyPublication>("ivyCustom") {
            descriptor {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
                author {
                    name.set("Jane Doe")
                    url.set("http://example.com/users/jane")
                }
                description {
                    text.set("A concise description of my library")
                    homepage.set("http://www.example.com/library")
                }
            }
        }
    }
// end::customize-descriptor[]
    repositories {
        ivy {
            // change to point to your repo, e.g. http://my.org/repo
            url = uri("$buildDir/repo")
        }
    }
}

// tag::generate[]
tasks.named<GenerateIvyDescriptor>("generateDescriptorFileForIvyCustomPublication") {
    destination = file("$buildDir/generated-ivy.xml")
}
// end::generate[]
