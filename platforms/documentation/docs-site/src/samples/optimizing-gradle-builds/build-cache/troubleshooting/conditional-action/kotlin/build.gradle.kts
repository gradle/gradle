if ("CI" in System.getenv()) {
    tasks.withType<Test>().configureEach {
        doFirst {
            println("Running test on CI")
        }
    }
}
