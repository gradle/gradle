// tag::avoid-this[]
plugins {
    kotlin("jvm").version("2.4.0-RC")
}

dependencies {
    api(kotlin("stdlib")) // <1>
}
// end::avoid-this[]
