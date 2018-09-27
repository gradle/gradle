tasks.create("hello") {
    doLast {
        println("hello")
    }
}

tasks {
    create<Copy>("copy") {
        from(file("srcDir"))
        into(buildDir)
    }
}
