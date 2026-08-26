// tag::avoid-this[]
plugins {
    kotlin("jvm").version("2.4.20-RC2")
}

dependencies {
    api(kotlin("stdlib")) // <1>
}
// end::avoid-this[]
