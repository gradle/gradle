sourceControl {
    vcsMappings {
        withModule("org.gradle.kotlin.dsl.samples.source-control:compute") {
            from(GitVersionControlSpec::class) {
                // A real life example would use a remote git repository
                url = uri("../external/build/git-repo")
            }
        }
    }
}
