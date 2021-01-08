// tag::use-build-dashboard-plugin[]
plugins {
    `build-dashboard`
// end::use-build-dashboard-plugin[]
    codenarc
    groovy
// tag::use-build-dashboard-plugin[]
}
// end::use-build-dashboard-plugin[]

repositories {
    mavenCentral()
}

dependencies {
    implementation(localGroovy())
    testImplementation("junit:junit:4.13")
}
