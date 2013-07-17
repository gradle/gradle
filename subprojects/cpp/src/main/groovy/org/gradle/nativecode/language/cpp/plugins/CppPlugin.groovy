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
import org.gradle.api.plugins.BasePlugin
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.language.base.ProjectSourceSet
import org.gradle.nativecode.base.*
import org.gradle.nativecode.base.internal.NativeBinaryInternal
import org.gradle.nativecode.base.plugins.NativeBinariesPlugin
import org.gradle.nativecode.base.tasks.*
import org.gradle.nativecode.language.asm.AssemblerSourceSet
import org.gradle.nativecode.language.c.CSourceSet
import org.gradle.nativecode.language.cpp.CppSourceSet
import org.gradle.nativecode.language.asm.internal.DefaultAssemblerSourceSet
import org.gradle.nativecode.language.c.internal.DefaultCSourceSet
import org.gradle.nativecode.language.cpp.internal.DefaultCppSourceSet
import org.gradle.nativecode.language.asm.tasks.Assemble
import org.gradle.nativecode.language.c.tasks.CCompile
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

                // Defaults for all cpp source sets
                functionalSourceSet.withType(CppSourceSet).all(new Action<CppSourceSet>() {
                    void execute(CppSourceSet sourceSet) {
                        sourceSet.exportedHeaders.srcDir "src/${functionalSourceSet.name}/headers"
                        sourceSet.source.srcDir "src/${functionalSourceSet.name}/cpp"
                    }
                })

                // TODO:DAZ Need to split out this convention from the rest of the base language support
                functionalSourceSet.add(instantiator.newInstance(DefaultCppSourceSet.class, "cpp", functionalSourceSet.getName(), project));

                // Defaults for all c source sets
                functionalSourceSet.withType(CSourceSet).all(new Action<CSourceSet>() {
                    void execute(CSourceSet sourceSet) {
                        sourceSet.exportedHeaders.srcDir "src/${functionalSourceSet.name}/headers"
                        sourceSet.source.srcDir "src/${functionalSourceSet.name}/c"
                    }
                })

                // TODO:DAZ Need to split out this convention from the rest of the base language support
                functionalSourceSet.add(instantiator.newInstance(DefaultCSourceSet.class, "c", functionalSourceSet.getName(), project));

                // Defaults for all assembler source sets
                functionalSourceSet.withType(AssemblerSourceSet).all(new Action<AssemblerSourceSet>() {
                    void execute(AssemblerSourceSet sourceSet) {
                        sourceSet.source.srcDir "src/${functionalSourceSet.name}/asm"
                    }
                })

                // TODO:DAZ Need to split out this convention from the rest of the base language support
                functionalSourceSet.add(instantiator.newInstance(DefaultAssemblerSourceSet.class, "asm", functionalSourceSet.getName(), project));
            }
        });

        project.binaries.withType(NativeBinary) { binary ->
            bindSourceSetLibsToBinary(binary)
            createTasks(project, binary)
        }
    }

    private static void bindSourceSetLibsToBinary(binary) {
        // TODO:DAZ Move this logic into NativeBinary (once we have laziness sorted)
        binary.source.withType(CppSourceSet).all { CppSourceSet sourceSet ->
            sourceSet.libs.each { NativeDependencySet lib ->
                binary.lib lib
            }
        }
        binary.source.withType(CSourceSet).all { CSourceSet sourceSet ->
            sourceSet.libs.each { NativeDependencySet lib ->
                binary.lib lib
            }
        }
    }

    def createTasks(ProjectInternal project, NativeBinaryInternal binary) {
        BuildBinaryTask buildBinaryTask
        if (binary instanceof StaticLibraryBinary) {
            buildBinaryTask = createStaticLibraryTask(project, binary)
        } else {
            buildBinaryTask = createLinkTask(project, binary)
        }
        binary.dependsOn buildBinaryTask

        binary.source.withType(CppSourceSet).all { CppSourceSet sourceSet ->
            def compileTask = createCompileTask(project, binary, sourceSet, CppCompile)
            buildBinaryTask.source compileTask.outputs.files.asFileTree.matching { include '**/*.obj', '**/*.o' }
        }

        binary.source.withType(CSourceSet).all { CSourceSet sourceSet ->
            def compileTask = createCompileTask(project, binary, sourceSet, CCompile)
            buildBinaryTask.source compileTask.outputs.files.asFileTree.matching { include '**/*.obj', '**/*.o' }
        }

        binary.source.withType(AssemblerSourceSet).all { AssemblerSourceSet sourceSet ->
            def compileTask = createAssembleTask(project, binary, sourceSet)
            buildBinaryTask.source compileTask.outputs.files.asFileTree.matching { include '**/*.obj', '**/*.o' }
        }

        if (binary instanceof ExecutableBinary) {
            createInstallTask(project, (NativeBinaryInternal) binary);
        }
    }

    private def createCompileTask(ProjectInternal project, NativeBinaryInternal binary, def sourceSet, def taskType) {
        def compileTask = project.task(binary.namingScheme.getTaskName("compile", sourceSet.fullName), type: taskType) {
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
        compileTask.conventionMapping.macros = { binary.macros }
        compileTask.conventionMapping.compilerArgs = { binary.compilerArgs }

        compileTask
    }

    private def createAssembleTask(ProjectInternal project, NativeBinaryInternal binary, def sourceSet) {
        def assembleTask = project.task(binary.namingScheme.getTaskName("assemble", sourceSet.fullName), type: Assemble) {
            description = "Assembles the $sourceSet sources of $binary"
        }

        assembleTask.toolChain = binary.toolChain

        assembleTask.source sourceSet.source

        assembleTask.conventionMapping.objectFileDir = { project.file("${project.buildDir}/objectFiles/${binary.namingScheme.outputDirectoryBase}/${sourceSet.fullName}") }
        assembleTask.conventionMapping.assemblerArgs = { binary.assemblerArgs }

        assembleTask
    }

    private AbstractLinkTask createLinkTask(ProjectInternal project, NativeBinaryInternal binary) {
        AbstractLinkTask linkTask = project.task(binary.namingScheme.getTaskName("link"), type: linkTaskType(binary)) {
             description = "Links ${binary}"
             group = BasePlugin.BUILD_GROUP
         }

        linkTask.toolChain = binary.toolChain

        binary.libs.each { NativeDependencySet lib ->
            linkTask.lib lib.linkFiles
        }

        linkTask.conventionMapping.outputFile = { binary.outputFile }
        linkTask.conventionMapping.linkerArgs = { binary.linkerArgs }
        return linkTask
    }

    private static Class<? extends AbstractLinkTask> linkTaskType(NativeBinary binary) {
        if (binary instanceof SharedLibraryBinary) {
            return LinkSharedLibrary
        }
        return LinkExecutable
    }

    private CreateStaticLibrary createStaticLibraryTask(ProjectInternal project, NativeBinaryInternal binary) {
        CreateStaticLibrary task = project.task(binary.namingScheme.getTaskName("create"), type: CreateStaticLibrary) {
             description = "Creates ${binary}"
             group = BasePlugin.BUILD_GROUP
         }

        task.toolChain = binary.toolChain
        task.conventionMapping.outputFile = { binary.outputFile }
        return task
    }

    def createInstallTask(ProjectInternal project, NativeBinaryInternal executable) {
        InstallExecutable installTask = project.task(executable.namingScheme.getTaskName("install"), type: InstallExecutable) {
            description = "Installs a development image of $executable"
            group = BasePlugin.BUILD_GROUP
        }

        installTask.conventionMapping.destinationDir = { project.file("${project.buildDir}/install/$executable.name") }

        installTask.conventionMapping.executable = { executable.outputFile }
        installTask.lib { executable.libs*.runtimeFiles }

        installTask.dependsOn(executable)
    }
}