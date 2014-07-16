package org.gradle.nativebinaries.language.assembler.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.assembler.plugins.AssemblerLangPlugin;
import org.gradle.nativebinaries.plugins.NativeComponentPlugin;

/**
 * A plugin for projects wishing to build native binary components from Assembly language sources.
 *
 * <p>Automatically includes the {@link AssemblerLangPlugin} for core Assembler support and the {@link NativeComponentPlugin} for native component support.</p>
 *
 * <li>Creates a {@link org.gradle.nativebinaries.language.assembler.tasks.Assemble} task for each {@link org.gradle.language.assembler.AssemblerSourceSet} to assemble the sources.</li>
 */
@Incubating
public class AssemblerPlugin implements Plugin<ProjectInternal> {
    public void apply(ProjectInternal project) {
        project.getPlugins().apply(NativeComponentPlugin.class);
        project.getPlugins().apply(AssemblerLangPlugin.class);
    }

}
