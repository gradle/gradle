allprojects {
    task("hello") {
        doLast {
            println("I'm ${this.project.name}")
        }
    }
}
