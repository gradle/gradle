plugins {
    scala
}

repositories {
    mavenCentral()
}

// tag::compiler-plugin[]
dependencies {
    implementation("org.scala-lang:scala-library:2.13.12")
    scalaCompilerPlugins("org.typelevel:kind-projector_2.13.12:0.13.2")
}
// end::compiler-plugin[]
