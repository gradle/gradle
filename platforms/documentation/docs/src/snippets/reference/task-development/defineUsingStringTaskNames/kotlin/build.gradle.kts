// tag::simple_register[]
tasks.register("hello") {
    doLast {
        println("hello")
    }
}
// end::simple_register[]

tasks.register<Copy>("copy") {
    from(file("srcDir"))
    into(buildDir)
}
