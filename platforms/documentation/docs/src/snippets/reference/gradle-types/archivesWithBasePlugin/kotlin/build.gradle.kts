// tag::create-archive-with-base-plugin-example[]
plugins {
    base
}

version = "1.0.0"

tasks.register<Zip>("packageDistribution") {
    from(layout.buildDirectory.dir("toArchive")) {
        exclude("**/*.pdf")
    }

    from(layout.buildDirectory.dir("toArchive")) {
        include("**/*.pdf")
        into("docs")
    }
}
// end::create-archive-with-base-plugin-example[]
