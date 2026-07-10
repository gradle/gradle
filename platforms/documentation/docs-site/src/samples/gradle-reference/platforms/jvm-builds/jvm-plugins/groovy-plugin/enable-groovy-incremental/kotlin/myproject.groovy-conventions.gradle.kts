tasks.withType<GroovyCompile>().configureEach {
    options.isIncremental = true
    options.incrementalAfterFailure = true
}
