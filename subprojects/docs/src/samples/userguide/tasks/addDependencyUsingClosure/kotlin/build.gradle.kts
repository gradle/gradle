val taskX by tasks.creating {
    doLast {
        println("taskX")
    }
}

// Using a Gradle Provider
taskX.dependsOn(provider {
    tasks.filter { task -> task.name.startsWith("lib") }
})

task("lib1") {
    doLast {
        println("lib1")
    }
}

task("lib2") {
    doLast {
        println("lib2")
    }
}

task("notALib") {
    doLast {
        println("notALib")
    }
}
