package org.gradle.nativebinaries.language.objectivecpp.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.objectivecpp.plugins.ObjectiveCppLangPlugin;
import org.gradle.nativebinaries.plugins.NativeComponentPlugin;

/**
 * A plugin for projects wishing to build native binary components from Objective-C++ sources.
 *
 * <p>Automatically includes the {@link ObjectiveCppLangPlugin} for core Objective-C++ support and the {@link NativeComponentPlugin} for native component support.</p>
 *
 * <li>Creates a {@link ObjectiveCppCompile} task for each {@link ObjectiveCppSourceSet} to compile the Objective-C++ sources.</li>
 */
@Incubating
public class ObjectiveCppPlugin implements Plugin<ProjectInternal> {
    public void apply(ProjectInternal project) {
        project.getPlugins().apply(NativeComponentPlugin.class);
        project.getPlugins().apply(ObjectiveCppLangPlugin.class);
    }

}
