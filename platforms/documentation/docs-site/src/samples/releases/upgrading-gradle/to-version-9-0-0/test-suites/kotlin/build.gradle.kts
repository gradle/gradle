testing {
    suites {
        named<JvmTestSuite>("test") {
            targets {
                register("otherTest")
            }
        }
    }
}
