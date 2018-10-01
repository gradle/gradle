allprojects {
    task("hello") {
        doLast {
            println("I'm ${this.project.name}")
        }
    }
}
subprojects {
    tasks.getByName("hello") {
        doLast {
            println("- I depend on water")
        }
    }
}
