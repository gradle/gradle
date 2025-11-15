// tag::use-war-plugin[]
plugins {
    war
}
// end::use-war-plugin[]

repositories {
    mavenCentral()
}

dependencies {
    implementation("commons-io:commons-io:2.15.1")
    implementation("log4j:log4j:1.2.15@jar")
}
