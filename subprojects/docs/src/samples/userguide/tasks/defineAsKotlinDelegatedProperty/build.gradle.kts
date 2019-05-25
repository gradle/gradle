// Using Kotlin delegated properties

val hello by tasks.registering {
    doLast {
        println("hello")
    }
}

val copy by tasks.registering(Copy::class) {
    from(file("srcDir"))
    into(buildDir)
}
