// tag::locking-classpath[]
buildscript {
    configurations.getByName("classpath") {
        resolutionStrategy.activateDependencyLocking()
    }
}
// end::locking-classpath[]
