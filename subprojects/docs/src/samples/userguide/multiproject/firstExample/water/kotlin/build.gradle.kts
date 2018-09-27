val cl = Action<Task> { println("I'm ${this.project.name}") }
task("hello").doLast(cl)
project(":bluewhale") {
    task("hello").doLast(cl)
}
