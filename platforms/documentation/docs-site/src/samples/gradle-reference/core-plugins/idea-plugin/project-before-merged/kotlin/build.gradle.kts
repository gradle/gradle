import org.gradle.plugins.ide.idea.model.Project

idea.project.ipr {
    beforeMerged(Action<Project> {
        modulePaths.clear()
    })
}
