plugins {
    id("java-library")
}
// tag::project-dependencies[]
dependencies {
    implementation(projects.utils)
    implementation(projects.api)
}
// end::project-dependencies[]
