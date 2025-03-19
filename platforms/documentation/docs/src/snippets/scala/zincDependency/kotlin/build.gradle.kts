plugins {
    id("scala")
}

repositories {
    mavenCentral()
}

// tag::zinc-dependency[]
scala {
    scalaVersion = "2.13.12"
    zincVersion = "1.10.4"
}
// end::zinc-dependency[]
