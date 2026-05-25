dependencies {
    implementation("org:foo:1.0")
    implementation("org:foo:1.0") {
        targetConfiguration = "conf"
    }
    implementation("org:foo:1.0:classifier")
    implementation("org:foo:1.0@ext")
}

testing.suites.named<JvmTestSuite>("test") {
    dependencies {
        implementation("org:foo:1.0")
    }
}
