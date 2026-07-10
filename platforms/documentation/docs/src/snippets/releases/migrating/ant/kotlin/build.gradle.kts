tasks.register("sayHello") {
    doLast {
        ant.withGroovyBuilder {
            "echo"("message" to "Hello!")
        }
    }
}
