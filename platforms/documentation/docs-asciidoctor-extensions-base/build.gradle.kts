plugins {
    id("gradlebuild.internal.tooling")
}

description = "Asciidoctor extensions that work with all backends"

dependencies {
    api(buildLibs.asciidoctor)
    api(buildLibs.asciidoctorApi)
    api(buildLibs.jspecify)

    implementation(buildLibs.commonsIo)
}

errorprone {
    nullawayEnabled = true
}
