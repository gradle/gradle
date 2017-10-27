sourceControl {
    vcsMappings {
        val computeGit = vcs(GitVersionControlSpec::class.java) {
            // A real life example would use a remote git repository
            url = uri("../external/build/git-repo")
        }
        withModule("org.gradle.kotlin.dsl.samples.source-control:compute") {
            from(computeGit)
        }
    }
}
