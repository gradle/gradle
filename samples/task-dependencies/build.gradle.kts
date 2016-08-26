val helloTask = task("hello") {
    doLast { println("Hello!") }
}

task("goodbye") {
    dependsOn(helloTask) // dependsOn task reference
    doLast { println("Goodbye!") }
}

task("chat") {
    dependsOn("goodbye") // dependsOn task name
}

task("mixItUp") {
    dependsOn(helloTask, "goodbye")
}

defaultTasks("chat")
