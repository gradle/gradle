plugins {
    id("scala")
}

repositories {
    mavenCentral()
}

// tag::scala-test-dependency[]
dependencies {
    testImplementation("org.scala-lang:scala-library:2.13.12")
}
// end::scala-test-dependency[]
