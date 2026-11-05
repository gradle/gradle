plugins {
    base
}

// tag::check-buildsrc[]
tasks.named("check") {
    dependsOn(gradle.includedBuild("buildSrc").task(":check"))
}
// end::check-buildsrc[]
