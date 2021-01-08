plugins {
    `java-library`
}

// tag::namespace[]
tasks.jar {
    archiveFileName.set("foo.jar")
}
// end::namespace[]

// tag::using-api[]
tasks.named<Jar>("jar") {
    archiveFileName.set("foo.jar")
}
// end::using-api[]

// tag::using-eager-api[]
tasks.getByName<Jar>("jar") {
    archiveFileName.set("foo.jar")
}
// end::using-eager-api[]
