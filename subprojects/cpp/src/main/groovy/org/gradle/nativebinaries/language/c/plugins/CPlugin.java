package org.gradle.nativebinaries.language.c.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.c.plugins.CLangPlugin;
import org.gradle.nativebinaries.plugins.NativeComponentPlugin;

/**
 * A plugin for projects wishing to build native binary components from C sources.
 *
 * <p>Automatically includes the {@link CLangPlugin} for core C++ support and the {@link org.gradle.nativebinaries.plugins.NativeComponentPlugin} for native component support.</p>
 *
 * <li>Creates a {@link org.gradle.nativebinaries.language.c.tasks.CCompile} task for each {@link org.gradle.language.c.CSourceSet} to compile the C sources.</li>
 */
@Incubating
public class CPlugin implements Plugin<ProjectInternal> {
    public void apply(ProjectInternal project) {
        project.getPlugins().apply(NativeComponentPlugin.class);
        project.getPlugins().apply(CLangPlugin.class);
    }

}
