
tasks {

    val hello by creating { // refactor friendly task definition
        doLast { println("Hello!") }
    }

    "goodbye" {
        dependsOn(hello)  // dependsOn task reference
        doLast { println("Goodbye!") }
    }

    "chat" {
        dependsOn("goodbye") // dependsOn task name
    }

    "mixItUp" {
        dependsOn(hello, "goodbye")
    }
}

defaultTasks("chat")
