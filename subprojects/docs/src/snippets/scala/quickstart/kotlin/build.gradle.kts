// tag::use-plugin[]
plugins {
    // end::use-plugin[]
    eclipse
// tag::use-plugin[]
    scala
}
// end::use-plugin[]

// tag::scala-dependency[]
repositories {
    mavenCentral()
}

dependencies {
    implementation("org.scala-lang:scala-library:2.11.12")
    testImplementation("org.scalatest:scalatest_2.11:3.0.0")
    testImplementation("junit:junit:4.12")
}
// end::scala-dependency[]

dependencies {
    implementation("commons-collections:commons-collections:3.2.2")
}
