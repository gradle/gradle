plugins {
    id("gradlebuild.internal.java")
    groovy
}

description = "Asciidoctor extensions that work with all backends"

dependencies {
    api(buildLibs.asciidoctor)
    api(buildLibs.asciidoctorApi)
    api(buildLibs.jspecify)

    implementation(buildLibs.commonsIo)
    testImplementation(testLibs.spock)
}

errorprone {
    nullawayEnabled = true
}
