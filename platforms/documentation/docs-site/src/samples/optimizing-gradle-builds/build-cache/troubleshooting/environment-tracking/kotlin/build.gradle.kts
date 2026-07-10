tasks.integTest {
    inputs.property("langEnvironment") {
        System.getenv("LANG")
    }
}
