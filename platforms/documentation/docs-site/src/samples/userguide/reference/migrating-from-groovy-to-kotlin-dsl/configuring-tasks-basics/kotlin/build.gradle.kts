plugins {
    `java-library`
}

// tag::namespace[]
tasks.jar {
    archiveFileName = "foo.jar"
}
// end::namespace[]

// tag::using-api[]
tasks.named<Jar>("jar") {
    archiveFileName = "foo.jar"
}
// end::using-api[]

// tag::using-eager-api[]
tasks.getByName<Jar>("jar") {
    archiveFileName = "foo.jar"
}
// end::using-eager-api[]
