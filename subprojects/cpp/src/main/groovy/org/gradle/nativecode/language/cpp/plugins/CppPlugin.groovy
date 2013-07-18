/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.nativecode.language.cpp.plugins
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.language.base.ProjectSourceSet
import org.gradle.nativecode.base.ExecutableBinary
import org.gradle.nativecode.base.NativeBinary
import org.gradle.nativecode.base.plugins.NativeBinariesPlugin
import org.gradle.nativecode.base.tasks.CreateStaticLibrary
import org.gradle.nativecode.base.tasks.LinkExecutable
import org.gradle.nativecode.base.tasks.LinkSharedLibrary
import org.gradle.nativecode.language.asm.AssemblerSourceSet
import org.gradle.nativecode.language.asm.internal.DefaultAssemblerSourceSet
import org.gradle.nativecode.language.asm.plugins.AssemblerLangPlugin
import org.gradle.nativecode.language.c.CSourceSet
import org.gradle.nativecode.language.c.internal.DefaultCSourceSet
import org.gradle.nativecode.language.c.plugins.CLangPlugin
import org.gradle.nativecode.language.cpp.CppSourceSet
import org.gradle.nativecode.language.cpp.internal.DefaultCppSourceSet
import org.gradle.nativecode.language.cpp.tasks.CppCompile
import org.gradle.nativecode.toolchain.plugins.GppCompilerPlugin
import org.gradle.nativecode.toolchain.plugins.MicrosoftVisualCppPlugin

import javax.inject.Inject
/**
 * A plugin for projects wishing to build custom components from C++ sources.
 * <p>Automatically includes {@link MicrosoftVisualCppPlugin} and {@link GppCompilerPlugin} for core toolchain support.</p>
 *
 * <p>
 *     For each {@link NativeBinary} found, this plugin will:
 *     <ul>
 *         <li>Create a {@link CppCompile} task named "compile${binary-name}" to compile the C++ sources.</li>
 *         <li>Create a {@link LinkExecutable} or {@link LinkSharedLibrary} task named "link${binary-name}
 *             or a {@link CreateStaticLibrary} task name "create${binary-name}" to create the binary artifact.</li>
 *         <li>Create an InstallTask named "install${Binary-name}" to install any {@link ExecutableBinary} artifact.
 *     </ul>
 * </p>
 */
@Incubating
class CppPlugin implements Plugin<ProjectInternal> {
    private final Instantiator instantiator;
    private final FileResolver fileResolver;

    @Inject
    public CppPlugin(Instantiator instantiator, FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.fileResolver = fileResolver;
    }

    void apply(ProjectInternal project) {
        project.plugins.apply(NativeBinariesPlugin)
        project.plugins.apply(MicrosoftVisualCppPlugin)
        project.plugins.apply(GppCompilerPlugin)

        ProjectSourceSet projectSourceSet = project.getExtensions().getByType(ProjectSourceSet.class);
        projectSourceSet.all(new Action<FunctionalSourceSet>() {
            public void execute(final FunctionalSourceSet functionalSourceSet) {
                applyCppConventions(project, functionalSourceSet)
                applyCConventions(project, functionalSourceSet)
                applyAssemblerConventions(project, functionalSourceSet)
            }
        });

        project.plugins.apply(CppLangPlugin)
        project.plugins.apply(CLangPlugin)
        project.plugins.apply(AssemblerLangPlugin)
    }

    private void applyCppConventions(ProjectInternal project, FunctionalSourceSet functionalSourceSet) {
        // Defaults for all cpp source sets
        functionalSourceSet.withType(CppSourceSet).all(new Action<CppSourceSet>() {
            void execute(CppSourceSet sourceSet) {
                sourceSet.exportedHeaders.srcDir "src/${functionalSourceSet.name}/headers"
                sourceSet.source.srcDir "src/${functionalSourceSet.name}/cpp"
            }
        })

        // Create a single C++ source set
        functionalSourceSet.add(instantiator.newInstance(DefaultCppSourceSet.class, "cpp", functionalSourceSet.getName(), project));
    }

    private void applyCConventions(ProjectInternal project, FunctionalSourceSet functionalSourceSet) {
        // Defaults for all c source sets
        functionalSourceSet.withType(CSourceSet).all(new Action<CSourceSet>() {
            void execute(CSourceSet sourceSet) {
                sourceSet.exportedHeaders.srcDir "src/${functionalSourceSet.name}/headers"
                sourceSet.source.srcDir "src/${functionalSourceSet.name}/c"
            }
        })

        // Create a single C source set
        functionalSourceSet.add(instantiator.newInstance(DefaultCSourceSet.class, "c", functionalSourceSet.getName(), project));
    }

    private void applyAssemblerConventions(ProjectInternal project, FunctionalSourceSet functionalSourceSet) {
        // Defaults for all assembler source sets
        functionalSourceSet.withType(AssemblerSourceSet).all(new Action<AssemblerSourceSet>() {
            void execute(AssemblerSourceSet sourceSet) {
                sourceSet.source.srcDir "src/${functionalSourceSet.name}/asm"
            }
        })

        // Create a single assembler source set
        functionalSourceSet.add(instantiator.newInstance(DefaultAssemblerSourceSet.class, "asm", functionalSourceSet.getName(), project));
    }
}