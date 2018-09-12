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
configure(subprojects.filter { it.name != "tropicalFish" }) {
    tasks.getByName("hello") {
        doLast {
            println("- I love to spend time in the arctic waters.")
        }
    }
}
