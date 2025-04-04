plugins {
    `java-library`
}

// tag::avoid-this[]
dependencies {
    implementation("org.jetbrains:kotlinx-coroutines-core:1.10.0") // <1>
    // ...
    // long dependencies declaration list continues
    // ...
    implementation("org.jetbrains:kotlinx-coroutines-core:1.6.0") // <2>
}
// end::avoid-this[]

// tag::do-this[]
dependencies {
    implementation("org.jetbrains:kotlinx-coroutines-core:1.10.0") // <1>
}
// end::do-this[]

// dummy task to be used in tests
tasks.register("dummyTask")
