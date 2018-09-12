val hello by tasks.creating {
    doLast {
        println("hello world")
    }
}

hello.onlyIf { !project.hasProperty("skipHello") }
