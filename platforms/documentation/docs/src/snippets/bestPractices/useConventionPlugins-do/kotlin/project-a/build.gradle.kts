// tag::do-this[]
plugins {
    id("my.java-common-conventions") // <1>
}

// Project-specific configuration only
dependencies {
    implementation("org.example:library:1.0.0") // <2>
}
// end::do-this[]
