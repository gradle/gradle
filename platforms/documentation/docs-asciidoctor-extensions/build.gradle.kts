plugins {
    id("gradlebuild.internal.java")
}

description = "Asciidoctor extensions that only work with html backends"

val asciiDoctorVersion = "2.5.13"

dependencies {
    api("org.asciidoctor:asciidoctorj-api:$asciiDoctorVersion")
    api("org.asciidoctor:asciidoctorj:$asciiDoctorVersion")

    implementation("commons-io:commons-io:2.11.0")
    implementation(projects.docsAsciidoctorExtensionsBase)
}

errorprone {
    nullawayEnabled = true
}
