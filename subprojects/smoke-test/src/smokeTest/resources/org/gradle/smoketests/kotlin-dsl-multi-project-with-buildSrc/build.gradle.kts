allprojects {
    declareHelloTask()
}

subprojects {
    hello.doLast {
        println("- I depend on water")
    }
}
