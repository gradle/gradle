plugins {
    id("gradlebuild.root-build")

    id("gradlebuild.lifecycle")                  // CI: Add lifecycle tasks to for the CI pipeline (currently needs to be applied early as it might modify global properties)
    id("gradlebuild.generate-subprojects-info")  // CI: Generate subprojects information for the CI testing pipeline fan out
    id("gradlebuild.cleanup")                    // CI: Advanced cleanup after the build (like stopping daemons started by tests)

    id("gradlebuild.update-versions")            // Local development: Convenience tasks to update versions in this build: 'released-versions.json', 'agp-versions.properties', ...
    id("gradlebuild.wrapper")                    // Local development: Convenience tasks to update the wrapper (like 'nightlyWrapper')
    id("gradlebuild.quick-check")                // Local development: Convenience task `quickCheck` for running checkstyle/codenarc only on changed files before commit
}
