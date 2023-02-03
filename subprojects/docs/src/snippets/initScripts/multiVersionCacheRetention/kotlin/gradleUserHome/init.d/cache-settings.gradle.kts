if (GradleVersion.current() >= GradleVersion.version("8.0")) {
    beforeSettings {
        // Use withGroovyBuilder since these model elements are not available in older Gradle versions
        withGroovyBuilder {
            "caches" {
                "releasedWrappers" { "setRemoveUnusedEntriesAfterDays"(45) }
                "snapshotWrappers" { "setRemoveUnusedEntriesAfterDays"(10) }
                "downloadedResources" { "setRemoveUnusedEntriesAfterDays"(45) }
                "createdResources" { "setRemoveUnusedEntriesAfterDays"(10) }
            }
        }
    }
}
