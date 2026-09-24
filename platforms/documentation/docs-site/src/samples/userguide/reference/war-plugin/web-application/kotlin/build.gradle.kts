// tag::use-war-plugin[]
plugins {
    war
}
// end::use-war-plugin[]

repositories {
    mavenCentral()
}

dependencies {
    implementation("commons-io:commons-io:1.4")
    implementation("log4j:log4j:1.2.15@jar")
}
