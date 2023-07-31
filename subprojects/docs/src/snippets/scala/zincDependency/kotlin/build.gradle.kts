plugins {
    scala
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.scala-lang:scala-library:2.12.1")
}

// tag::zinc-dependency[]
scala {
    zincVersion = "1.6.1"
}
// end::zinc-dependency[]
