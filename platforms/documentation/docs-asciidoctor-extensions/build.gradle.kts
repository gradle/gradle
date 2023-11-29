plugins {
    id("gradlebuild.internal.java")
}

description = "Asciidoctor extensions that only work with html backends"

dependencies {
    implementation("commons-io:commons-io:2.11.0")
    implementation("org.asciidoctor:asciidoctorj:2.4.3")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(project(":docs-asciidoctor-extensions-base"))
}
