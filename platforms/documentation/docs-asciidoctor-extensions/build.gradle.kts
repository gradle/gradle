plugins {
    id("gradlebuild.internal.java")
}

description = "Asciidoctor extensions that only work with html backends"

val asciiDoctorVersion = "2.4.3"

dependencies {
    api("org.asciidoctor:asciidoctorj-api:$asciiDoctorVersion")
    api("org.asciidoctor:asciidoctorj:$asciiDoctorVersion")

    implementation("commons-io:commons-io:2.11.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(project(":docs-asciidoctor-extensions-base"))
}
