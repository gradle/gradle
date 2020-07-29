allprojects {
    tasks.register("hello") {
        doLast {
            println("I'm ${this.project.name}")
        }
    }
}
subprojects {
    tasks.named("hello") {
        doLast {
            println("- I depend on water")
        }
    }
}
