plugins {
    id("scala")
}

// tag::scala-dependency[]
repositories {
    mavenCentral()
}

dependencies {
    implementation("org.scala-lang:scala3-library_3:3.6.3")
}
// end::scala-dependency[]
