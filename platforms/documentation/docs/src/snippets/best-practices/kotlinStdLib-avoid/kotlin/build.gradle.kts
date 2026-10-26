// tag::avoid-this[]
plugins {
    kotlin("jvm").version("2.5.20-dev-2076")
}

dependencies {
    api(kotlin("stdlib")) // <1>
}
// end::avoid-this[]
