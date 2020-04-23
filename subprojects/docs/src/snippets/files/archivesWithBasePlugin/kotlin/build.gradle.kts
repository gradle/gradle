// tag::create-archive-with-base-plugin-example[]
plugins {
    base
}

version = "1.0.0"

tasks.register<Zip>("packageDistribution") {
    from("$buildDir/toArchive") {
        exclude("**/*.pdf")
    }

    from("$buildDir/toArchive") {
        include("**/*.pdf")
        into("docs")
    }
}
// end::create-archive-with-base-plugin-example[]
