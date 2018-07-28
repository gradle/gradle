
tasks {

    val hello by creating { // refactor friendly task definition
        doLast { println("Hello!") }
    }

    register("goodbye") {
        dependsOn(hello)  // dependsOn task reference
        doLast { println("Goodbye!") }
    }

    register("chat") {
        dependsOn("goodbye") // dependsOn task name
    }

    register("mixItUp") {
        dependsOn(hello, "goodbye")
    }
}

defaultTasks("chat")
