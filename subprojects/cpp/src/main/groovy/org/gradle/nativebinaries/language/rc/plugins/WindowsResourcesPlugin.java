package org.gradle.nativebinaries.language.rc.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.rc.plugins.WindowsResourceScriptPlugin;
import org.gradle.nativebinaries.plugins.NativeComponentPlugin;

/**
 * A plugin for projects wishing to build native binary components from Windows Resource sources.
 *
 * <p>Automatically includes the {@link WindowsResourceScriptPlugin} for core Windows Resource source support and the {@link NativeComponentPlugin} for native component support.</p>
 *
 * <li>Creates a {@link WindowsResourceCompile} task for each {@link WindowsResourceSet} to compile the sources.</li>
 */
@Incubating
public class WindowsResourcesPlugin implements Plugin<ProjectInternal> {
    public void apply(ProjectInternal project) {
        project.getPlugins().apply(NativeComponentPlugin.class);
        project.getPlugins().apply(WindowsResourceScriptPlugin.class);
    }

}
