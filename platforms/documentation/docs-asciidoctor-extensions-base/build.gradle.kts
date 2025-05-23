plugins {
    id("gradlebuild.internal.java")
    groovy
}

description = "Asciidoctor extensions that work with all backends"

val asciiDoctorVersion = "3.0.0"

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 1 occurrences
    )
}

dependencies {
    api("org.asciidoctor:asciidoctorj-api:$asciiDoctorVersion")
    api("org.asciidoctor:asciidoctorj:$asciiDoctorVersion")

    implementation("commons-io:commons-io:2.11.0")
    testImplementation("org.spockframework:spock-core")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}
