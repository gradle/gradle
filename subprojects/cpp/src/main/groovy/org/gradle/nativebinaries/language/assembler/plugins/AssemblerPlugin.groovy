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
package org.gradle.nativebinaries.language.assembler.plugins
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.language.assembler.AssemblerSourceSet
import org.gradle.language.assembler.plugins.AssemblerLangPlugin
import org.gradle.model.ModelRule
import org.gradle.model.ModelRules
import org.gradle.nativebinaries.Executable
import org.gradle.nativebinaries.Library
import org.gradle.nativebinaries.ProjectNativeBinary
import org.gradle.nativebinaries.ProjectNativeComponent
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal
import org.gradle.nativebinaries.language.assembler.tasks.Assemble
import org.gradle.nativebinaries.plugins.NativeBinariesPlugin
import org.gradle.nativebinaries.toolchain.Clang
import org.gradle.nativebinaries.toolchain.Gcc
import org.gradle.nativebinaries.toolchain.internal.ToolChainRegistryInternal
import org.gradle.nativebinaries.toolchain.internal.ToolType
import org.gradle.nativebinaries.toolchain.internal.tools.DefaultTool

import javax.inject.Inject

/**
 * A plugin for projects wishing to build native binary components from Assembly language sources.
 *
 * <p>Automatically includes the {@link AssemblerLangPlugin} for core Assembler support and the {@link NativeBinariesPlugin} for native binary support.</p>
 *
 * <li>Creates a {@link Assemble} task for each {@link AssemblerSourceSet} to assemble the sources.</li>
 */
@Incubating
class AssemblerPlugin implements Plugin<ProjectInternal> {

    private ModelRules modelRules

    @Inject
    public AssemblerPlugin(ModelRules modelRules){
        this.modelRules = modelRules
    }

    void apply(ProjectInternal project) {
        project.plugins.apply(NativeBinariesPlugin)
        project.plugins.apply(AssemblerLangPlugin)

        modelRules.rule(new ModelRule() {
            void addAssembler(ToolChainRegistryInternal toolChainRegistry) {
                toolChainRegistry.withType(Clang).all(new Action<Clang>(){
                    void execute(Clang toolchain) {
                        toolchain.add(new DefaultTool("assembler", ToolType.ASSEMBLER, "as"));
                    }
                })

                toolChainRegistry.withType(Gcc).all(new Action<Gcc>(){
                    void execute(Gcc toolchain) {
                        toolchain.add(new DefaultTool("assembler", ToolType.ASSEMBLER, "as"));
                    }
                })
            }
        });

        project.executables.all { Executable executable ->
            addLanguageExtensionsToComponent(executable)
        }
        project.libraries.all { Library library ->
            addLanguageExtensionsToComponent(library)
        }

        project.binaries.withType(ProjectNativeBinary) { ProjectNativeBinaryInternal binary ->
            binary.source.withType(AssemblerSourceSet).all { AssemblerSourceSet sourceSet ->
                if (sourceSet.mayHaveSources) {
                    def assembleTask = createAssembleTask(project, binary, sourceSet)
                    assembleTask.dependsOn sourceSet
                    binary.tasks.add assembleTask
                    binary.tasks.builder.source assembleTask.outputs.files.asFileTree.matching { include '**/*.obj', '**/*.o' }
                }
            }
        }
    }

    private def addLanguageExtensionsToComponent(ProjectNativeComponent component) {
        component.binaries.all { binary ->
            binary.extensions.create("assembler", org.gradle.nativebinaries.internal.DefaultTool)
        }
    }

    private def createAssembleTask(ProjectInternal project, ProjectNativeBinaryInternal binary, def sourceSet) {
        def assembleTask = project.task(binary.namingScheme.getTaskName("assemble", sourceSet.fullName), type: Assemble) {
            description = "Assembles the $sourceSet of $binary"
        }

        assembleTask.toolChain = binary.toolChain
        assembleTask.targetPlatform = binary.targetPlatform

        assembleTask.source sourceSet.source

        assembleTask.objectFileDir = project.file("${project.buildDir}/objectFiles/${binary.namingScheme.outputDirectoryBase}/${sourceSet.fullName}")
        assembleTask.assemblerArgs = binary.assembler.args

        assembleTask
    }
}