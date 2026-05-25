tasks.withType<Test>().configureEach {
    doFirst {
        if ("CI" in System.getenv()) {
            println("Running test on CI")
        }
    }
}
