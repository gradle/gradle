// tag::platform-example[]
plugins {
    `java-platform`
}

dependencies {
    constraints {
        api("com.example:mylib:2.0.0")
    }
}
// end::platform-example[]
