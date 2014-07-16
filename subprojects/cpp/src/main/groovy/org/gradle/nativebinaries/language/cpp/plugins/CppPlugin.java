package org.gradle.nativebinaries.language.cpp.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.cpp.plugins.CppLangPlugin;
import org.gradle.nativebinaries.plugins.NativeComponentPlugin;

/**
 * A plugin for projects wishing to build native binary components from C++ sources.
 *
 * <p>Automatically includes the {@link CppLangPlugin} for core C++ support and the {@link NativeComponentPlugin} for native component support.</p>
 *
 * <li>Creates a {@link org.gradle.nativebinaries.language.cpp.tasks.CppCompile} task for each {@link org.gradle.language.cpp.CppSourceSet} to compile the C++ sources.</li>
 */
@Incubating
public class CppPlugin implements Plugin<ProjectInternal> {
    public void apply(ProjectInternal project) {
        project.getPlugins().apply(NativeComponentPlugin.class);
        project.getPlugins().apply(CppLangPlugin.class);
    }

}
