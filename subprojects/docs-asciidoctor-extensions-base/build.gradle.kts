plugins {
    id("gradlebuild.internal.java")
    groovy
}

description = "Asciidoctor extensions that work with all backends"

dependencies {
    implementation("commons-io:commons-io:2.11.0")
    implementation("org.asciidoctor:asciidoctorj:2.4.3")
    testImplementation("org.spockframework:spock-core")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
