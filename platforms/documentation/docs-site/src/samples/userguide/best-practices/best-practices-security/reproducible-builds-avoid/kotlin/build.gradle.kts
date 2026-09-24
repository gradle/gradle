// tag::avoid-this[]
plugins {
    `java-library` // <1>
}

tasks.named<Jar>("jar") {
    isPreserveFileTimestamps = true   // <2>
    isReproducibleFileOrder = false   // <3>
}
// end::avoid-this[]
