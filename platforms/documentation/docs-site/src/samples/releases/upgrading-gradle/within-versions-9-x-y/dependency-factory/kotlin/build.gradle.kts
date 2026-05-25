val group = "org"
val artifactId = "foo"
val version = "1.0"

configurations.dependencyScope("implementation") {
    dependencies.add(project.dependencyFactory.create(group, artifactId, version))
}
