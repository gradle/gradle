val hello = tasks.register("hello") {
    doLast {
        println("hello world")
    }
}

hello {
    val skipProvider = providers.gradleProperty("skipHello")
    onlyIf("there is no property skipHello") {
        !skipProvider.isPresent()
    }
}
