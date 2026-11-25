plugins {
    id("gradlebuild.internal.tooling")
}

description = "Asciidoctor extensions that only work with html backends"


dependencies {
    implementation(buildLibs.commonsIo)
    implementation(projects.docsAsciidoctorExtensionsBase)
}

errorprone {
    nullawayEnabled = true
}
