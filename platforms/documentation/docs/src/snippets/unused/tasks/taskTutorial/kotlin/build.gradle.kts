// tag::app[]
plugins {
    id("application")
}
// end::app[]

application {
    mainClass.set("com.example.Hello")
}

// tag::task[]
tasks.register("hello") {
    group = "Custom"
    description = "A lovely greeting task."
    doLast {
        println("Hello world!")
    }
}
// end::task[]

// tag::copy[]
tasks.register<Copy>("copyTask") {
    from("source")
    into("target")
    include("*.war")
}
// end::copy[]
