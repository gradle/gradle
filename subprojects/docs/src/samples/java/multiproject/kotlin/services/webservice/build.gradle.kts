plugins {
    war
}

version = "2.5"

// tag::dependency-configurations[]
dependencies {
// end::dependency-configurations[]
    implementation(project(":shared"))
    implementation("commons-collections:commons-collections:3.2.2@jar")
    implementation("commons-io:commons-io:2.6")
    implementation("org.apache.commons:commons-lang3:3.7@jar")
// tag::dependency-configurations[]
    implementation(project(path = ":api", configuration = "spi"))
// end::dependency-configurations[]
    runtimeOnly(project(":api"))
// tag::dependency-configurations[]
}
// end::dependency-configurations[]
