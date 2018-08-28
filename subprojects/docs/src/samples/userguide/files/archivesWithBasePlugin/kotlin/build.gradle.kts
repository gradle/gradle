// tag::create-archive-with-base-plugin-example[]
plugins {
    base
}

version = "1.0.0"

task<Zip>("packageDistribution") {
    from("$buildDir/toArchive") {
        exclude("**/*.pdf")
    }

    from("$buildDir/toArchive") {
        include("**/*.pdf")
        into("docs")
    }
}
// end::create-archive-with-base-plugin-example[]
