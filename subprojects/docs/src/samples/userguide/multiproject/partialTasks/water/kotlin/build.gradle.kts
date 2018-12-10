allprojects {
    tasks.register("hello") {
        doLast {
            println("I'm ${this.project.name}")
        }
    }
}
subprojects {
    tasks.named("hello") {
        doLast { println("- I depend on water") }
        afterEvaluate {
            if (extra["arctic"] as Boolean) {
                doLast { println("- I love to spend time in the arctic waters.") }
            }
        }
    }
}

