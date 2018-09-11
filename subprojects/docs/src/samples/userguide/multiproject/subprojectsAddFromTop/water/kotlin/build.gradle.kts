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
project(":bluewhale").tasks.getByName("hello") {
    doLast {
        println("- I'm the largest animal that has ever lived on this planet.")
    }
}
