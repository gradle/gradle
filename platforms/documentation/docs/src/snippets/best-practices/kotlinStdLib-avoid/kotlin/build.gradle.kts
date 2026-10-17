// tag::avoid-this[]
plugins {
    kotlin("jvm").version("2.5.0-dev-9824")
}

dependencies {
    api(kotlin("stdlib")) // <1>
}
// end::avoid-this[]
