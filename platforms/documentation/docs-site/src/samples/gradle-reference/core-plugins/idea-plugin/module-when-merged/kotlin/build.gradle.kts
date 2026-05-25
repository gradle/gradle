import org.gradle.plugins.ide.idea.model.Module
import org.gradle.plugins.ide.idea.model.ModuleDependency

idea.module.iml {
    whenMerged(Action<Module> {
        dependencies.forEach {
            (it as ModuleDependency).isExported = true
        }
    })
}
