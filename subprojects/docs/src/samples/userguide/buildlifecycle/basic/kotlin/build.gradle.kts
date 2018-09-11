println("This is executed during the configuration phase.")

task("configured") {
    println("This is also executed during the configuration phase.")
}

task("test") {
    doLast {
        println("This is executed during the execution phase.")
    }
}

task("testBoth") {
    doFirst {
        println("This is executed first during the execution phase.")
    }
    doLast {
        println("This is executed last during the execution phase.")
    }
    println("This is executed during the configuration phase as well.")
}
