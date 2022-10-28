val hello by tasks.registering {
    doLast {
        println("hello world")
    }
}

hello {
    onlyIf("there is no property skipHello") { !project.hasProperty("skipHello") }
}
