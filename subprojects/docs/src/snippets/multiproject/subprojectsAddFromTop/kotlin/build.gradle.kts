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
project(":bluewhale").tasks.named("hello") {
    doLast {
        println("- I'm the largest animal that has ever lived on this planet.")
    }
}
