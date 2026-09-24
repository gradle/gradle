plugins {
    id("scala")
}

// tag::scala-version[]
repositories {
    mavenCentral()
}

scala {
    scalaVersion = "3.6.3"
}
// end::scala-version[]

dependencies {
    implementation("org.scala-lang:scala3-library_3:3.0.1")
    testImplementation("org.scalatest:scalatest_3:3.2.9")
    testImplementation("junit:junit:4.13")
    implementation("commons-collections:commons-collections:3.2.2")
}
