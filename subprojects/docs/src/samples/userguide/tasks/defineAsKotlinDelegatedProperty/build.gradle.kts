// Using Kotlin delegated properties

val hello by tasks.creating {
    doLast {
        println("hello")
    }
}

val copy by tasks.creating(Copy::class) {
    from(file("srcDir"))
    into(buildDir)
}
