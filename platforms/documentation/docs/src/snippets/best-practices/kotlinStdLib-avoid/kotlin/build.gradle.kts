// tag::avoid-this[]
plugins {
    kotlin("jvm").version("2.3.20")
}

dependencies {
    api(kotlin("stdlib")) // <1>
}
// end::avoid-this[]
