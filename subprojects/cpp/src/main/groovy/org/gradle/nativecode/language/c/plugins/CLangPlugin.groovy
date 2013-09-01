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
package org.gradle.nativecode.language.c.plugins

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.plugins.LanguageBasePlugin
import org.gradle.nativecode.base.NativeBinary
import org.gradle.nativecode.base.NativeDependencySet
import org.gradle.nativecode.base.SharedLibraryBinary
import org.gradle.nativecode.base.ToolChainTool
import org.gradle.nativecode.base.internal.NativeBinaryInternal
import org.gradle.nativecode.language.c.CSourceSet
import org.gradle.nativecode.language.c.internal.DefaultCSourceSet
import org.gradle.nativecode.language.c.tasks.CCompile

import javax.inject.Inject

@Incubating
class CLangPlugin implements Plugin<ProjectInternal> {
    private final Instantiator instantiator;

    @Inject
    public CLangPlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    void apply(ProjectInternal project) {
        project.getPlugins().apply(LanguageBasePlugin.class);

        ProjectSourceSet projectSourceSet = project.getExtensions().getByType(ProjectSourceSet.class);
        projectSourceSet.all(new Action<FunctionalSourceSet>() {
            public void execute(final FunctionalSourceSet functionalSourceSet) {
                applyConventions(project, functionalSourceSet)
            }
        });

        // TODO:DAZ Clean this up (would be simpler if it could just apply to all binaries)
        project.executables.all {
            it.binaries.all {
                ext.cCompiler = new ToolChainTool()
            }
        }
        project.libraries.all {
            it.binaries.all {
                ext.cCompiler = new ToolChainTool()
            }
        }

        project.binaries.withType(NativeBinary) { NativeBinaryInternal binary ->
            binary.source.withType(CSourceSet).all { CSourceSet sourceSet ->
                def compileTask = createCompileTask(project, binary, sourceSet)
                binary.builderTask.source compileTask.outputs.files.asFileTree.matching { include '**/*.obj', '**/*.o' }
            }
        }
    }


    private void applyConventions(ProjectInternal project, FunctionalSourceSet functionalSourceSet) {
        // Defaults for all C source sets
        functionalSourceSet.withType(CSourceSet).all { sourceSet ->
            sourceSet.exportedHeaders.srcDir "src/${functionalSourceSet.name}/headers"
            sourceSet.source.srcDir "src/${functionalSourceSet.name}/c"
        }

        // Create a single C source set
        functionalSourceSet.add(instantiator.newInstance(DefaultCSourceSet.class, "c", functionalSourceSet, project));
    }


    private def createCompileTask(ProjectInternal project, NativeBinaryInternal binary, def sourceSet) {
        def compileTask = project.task(binary.namingScheme.getTaskName("compile", sourceSet.fullName), type: CCompile) {
            description = "Compiles the $sourceSet sources of $binary"
        }

        compileTask.toolChain = binary.toolChain
        compileTask.positionIndependentCode = binary instanceof SharedLibraryBinary

        compileTask.includes sourceSet.exportedHeaders
        compileTask.source sourceSet.source
        binary.libs.each { NativeDependencySet deps ->
            compileTask.includes deps.includeRoots
        }

        compileTask.conventionMapping.objectFileDir = { project.file("${project.buildDir}/objectFiles/${binary.namingScheme.outputDirectoryBase}/${sourceSet.fullName}") }
        compileTask.macros = binary.macros
        compileTask.compilerArgs = binary.cCompiler.args

        compileTask
    }
}