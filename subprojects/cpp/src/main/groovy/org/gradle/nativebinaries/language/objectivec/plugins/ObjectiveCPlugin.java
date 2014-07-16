package org.gradle.nativebinaries.language.objectivec.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.objectivec.plugins.ObjectiveCLangPlugin;
import org.gradle.nativebinaries.plugins.NativeComponentPlugin;

/**
 * A plugin for projects wishing to build native binary components from Objective-C sources.
 *
 * <p>Automatically includes the {@link ObjectiveCLangPlugin} for core Objective-C support and the {@link org.gradle.nativebinaries.plugins.NativeComponentPlugin} for native component support.</p>
 *
 * <li>Creates a {@link org.gradle.nativebinaries.language.objectivec.tasks.ObjectiveCCompile} task for each {@link org.gradle.language.objectivec.ObjectiveCSourceSet} to compile the Objective-C sources.</li>
 */
@Incubating
public class ObjectiveCPlugin implements Plugin<ProjectInternal> {
    public void apply(ProjectInternal project) {
        project.getPlugins().apply(NativeComponentPlugin.class);
        project.getPlugins().apply(ObjectiveCLangPlugin.class);
    }

}
