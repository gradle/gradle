val hello by tasks.registering {
    doLast {
        println("Hello Earth")
    }
}
hello {
    doFirst {
        println("Hello Venus")
    }
}
hello {
    doLast {
        println("Hello Mars")
    }
}
hello {
    doLast {
        println("Hello Jupiter")
    }
}
