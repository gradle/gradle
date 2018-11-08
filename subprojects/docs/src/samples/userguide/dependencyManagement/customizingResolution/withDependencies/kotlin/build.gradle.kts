// tag::configuration-with-dependencies[]
configurations {
    create("implementation") {
        withDependencies {
            add(project.dependencies.create("org.gradle:my-util:1.0"))
        }
    }
}
// end::configuration-with-dependencies[]
