allprojects {
    tasks.register("hello") {
        doLast {
            println("I'm ${this.project.name}")
        }
    }
}
subprojects {
    val hello by tasks.existing

    hello {
        doLast { println("- I depend on water") }
    }

    afterEvaluate {
        if (extra["arctic"] as Boolean) {
            hello {
                doLast {
                    println("- I love to spend time in the arctic waters.")
                }
            }
        }
    }
}
