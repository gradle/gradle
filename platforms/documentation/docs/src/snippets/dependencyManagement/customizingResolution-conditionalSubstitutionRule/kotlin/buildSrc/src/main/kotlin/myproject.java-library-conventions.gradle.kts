plugins {
    `java-library`
}

// tag::custom-repo[]
repositories {
    ivy { url = rootProject.layout.projectDirectory.dir("repo") }
}
// end::custom-repo[]
