plugins {
    `java-library`
}

dependencies {
    implementation(libs.jackson)
    implementation(libs.jackson.databind)
}

// tag::use_version_asprovider[]
val jacksonVersion = libs.versions.jackson.asProvider()
// end::use_version_asprovider[]

tasks.register("checkVersion") {
    val version = jacksonVersion
    doLast {
        println("Jackson version: ${version.get()}")
    }
}
