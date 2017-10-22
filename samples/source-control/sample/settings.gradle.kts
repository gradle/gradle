sourceControl {
    vcsMappings {
        val computeGit = vcs(GitVersionControlSpec::class.java) {
            // A real life example would use a remote git repository
            setUrl(File(rootDir, "../external/build/git-repo").toURI())
        }
        withModule("org.gradle.kotlin.dsl.samples.source-control:compute") {
            from(computeGit)
        }
    }
}
