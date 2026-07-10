val test by testing.suites.existing(JvmTestSuite::class)
tasks.register<Test>("otherTest") {
    testClassesDirs = files(test.map { it.sources.output.classesDirs })
    classpath = files(test.map { it.sources.runtimeClasspath })
}
