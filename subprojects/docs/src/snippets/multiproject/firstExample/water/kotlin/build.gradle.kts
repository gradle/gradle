val cl = Action<Task> { println("I'm ${this.project.name}") }
tasks.register("hello") { doLast(cl) }
project(":bluewhale") {
    tasks.register("hello") { doLast(cl) }
}
