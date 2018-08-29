// tag::use-plugin[]
plugins {
    `java-library`
}
// end::use-plugin[]


// tag::repo[]
repositories {
    mavenCentral()
}
// end::repo[]

// tag::dependencies[]
dependencies {
    api("commons-httpclient:commons-httpclient:3.1")
    implementation("org.apache.commons:commons-lang3:3.5")
}
// end::dependencies[]
