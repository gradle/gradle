plugins {
    id("scala")
}

// tag::scala-dependency[]
repositories {
    mavenCentral()
}

dependencies {
    implementation("org.scala-lang:scala-library:2.13.12")
}
// end::scala-dependency[]
