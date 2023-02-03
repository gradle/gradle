// tag::locking-classpath[]
buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}
// end::locking-classpath[]
