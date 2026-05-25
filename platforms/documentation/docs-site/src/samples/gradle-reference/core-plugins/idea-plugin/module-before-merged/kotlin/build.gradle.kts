import org.gradle.plugins.ide.idea.model.Module

idea.module.iml {
    beforeMerged(Action<Module> {
        dependencies.clear()
    })
}
