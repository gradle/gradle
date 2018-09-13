sourceControl {
    // A real life example would use a remote git repository
    gitRepository(uri("../external/build/git-repo")) {
        producesModule("org.gradle.kotlin.dsl.samples.source-control:compute")
    }
}
