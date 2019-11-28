val hello by tasks.registering {
    doLast {
        println("hello world")
    }
}

hello {
    onlyIf { !project.hasProperty("skipHello") }
}
