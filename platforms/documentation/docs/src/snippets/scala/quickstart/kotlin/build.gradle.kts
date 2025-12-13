// tag::use-plugin[]
plugins {
    // end::use-plugin[]
    id("eclipse")
// tag::use-plugin[]
    id("scala")
}
// end::use-plugin[]

// tag::scala-version[]
repositories {
    mavenCentral()
}

scala {
    scalaVersion = "2.13.12"
}
// end::scala-version[]

dependencies {
    implementation("commons-collections:commons-collections:3.2.2")
    testImplementation("junit:junit:4.13")
}
