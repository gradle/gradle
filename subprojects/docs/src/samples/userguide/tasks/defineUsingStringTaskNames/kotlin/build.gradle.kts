task("hello") {
    doLast {
        println("hello")
    }
}

task<Copy>("copy") {
    from(file("srcDir"))
    into(buildDir)
}
