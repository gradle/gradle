dependencies {
    implementation(group = "org", name = "foo", version = "1.0")
    implementation(group = "org", name = "foo", version = "1.0", configuration = "conf")
    implementation(group = "org", name = "foo", version = "1.0", classifier = "classifier")
    implementation(group = "org", name = "foo", version = "1.0", ext = "ext")
}

testing.suites.named<JvmTestSuite>("test") {
    dependencies {
        implementation(module(group = "org", name = "foo", version = "1.0"))
    }
}
