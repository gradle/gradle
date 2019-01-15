allprojects {
    tasks.register("hello") {
        doLast {
            println("I'm ${this.project.name}")
        }
    }
}
