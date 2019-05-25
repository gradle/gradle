val taskX by tasks.registering {
    doLast {
        println("taskX")
    }
}

// Using a Gradle Provider
taskX {
    dependsOn(provider {
        tasks.filter { task -> task.name.startsWith("lib") }
    })
}

tasks.register("lib1") {
    doLast {
        println("lib1")
    }
}

tasks.register("lib2") {
    doLast {
        println("lib2")
    }
}

tasks.register("notALib") {
    doLast {
        println("notALib")
    }
}
