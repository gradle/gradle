// tag::configuration-with-dependencies[]
configurations {
    create("implementation") {
        withDependencies {
            val dep = this.find { it.name == "to-modify" } as ExternalModuleDependency
            dep.version {
                strictly("1.2")
            }
        }
    }
}
// end::configuration-with-dependencies[]
