plugins {
    `java-library`
}

// tag::custom-repo[]
repositories {
    ivy { url = uri(rootProject.layout.projectDirectory.dir("repo")) }
}
// end::custom-repo[]
