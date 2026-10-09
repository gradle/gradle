// tag::avoid-this[]
plugins {
    kotlin("jvm").version("2.5.0-dev-9401")
}

dependencies {
    api(kotlin("stdlib")) // <1>
}
// end::avoid-this[]
