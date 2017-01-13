/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testing.performance.generator.tasks

import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.nativeplatform.NativeExecutableSpec
import org.gradle.nativeplatform.NativeLibrarySpec
/**
 * Generates a multi-project native build that has project dependencies and tests.
 */
class NativeProjectWithDepsGeneratorTask extends ProjectGeneratorTask {

    /** Represents a native library requirement */
    class Dependency {
        String project
        String library
        String linkage

        Dependency(String project, String library, String linkage) {
            this.project = project
            this.library = library
            this.linkage = linkage
        }
    }

    /** Represents a component */
    class Component {
        String name
        String type
        List<Dependency> deps

        Component(String name, String type, List<Dependency> deps) {
            this.name = name
            this.type = type
            this.deps = deps
        }
    }

    /**
     * Number of components to generate per project
     */
    @Input
    int numberOfComponents = 4

    /**
     * Should we generate a deep and wide hierarchy ("worst case" scenario) or only a wide hierarchy?
     * <p>
     *     With a wide hierarchy, each group of libraries depend only on each other. There's a limited depth (~7) and
     *     several projects.  This tries to replicate a project with many subgroups of interconnected components.
     * <p>
     *     With a deep and wide hierarchy, each subproject is formed as above, except each group of libraries depend on
     *     the previous group as well.  This means each group of libraries is deeper than the last.  This tries to
     *     replicate a project with many common libraries that are used extensively.
     */
    @Input
    boolean generateDeepHierarchy = false

    /**
     * Extra files that should be copied into the root directory
     *
     * These files do not have any filtering/expanding done to them.
     */
    @InputFiles
    ConfigurableFileCollection resources = project.files()

    /**
     * @return names of all generated subprojects
     */
    @Input
    List<String> getSubprojectNames() {
        getGeneratedLibraries().flatten() + getGeneratedExecutables().flatten()
    }

    /**
     * Template directory with source and build file templates
     */
    @Input
    String projectTemplateName = "native-dependents"

    /**
     * @return Template directory with source and build file templates
     */
    @InputDirectory
    public File getProjectTemplate() {
        resolveTemplate(projectTemplateName)
    }

    @TaskAction
    void generate() {
        copyResources()
        generateRootProject()
        generateSubprojects()
    }

    void generateRootProject() {
        generateSettings()
    }

    // TODO: This could be made more generic by passing a list of subproject names
    // that includes 'googleTest'.
    void generateSettings() {
        project.copy(new Action<CopySpec>() {
            @Override
            void execute(CopySpec copySpec) {
                copySpec.from(new File(projectTemplate, "settings.gradle"))
                copySpec.into(destDir)
                copySpec.expand([
                        rootProjectName: name,
                        subprojects: subprojectNames
                ])
            }
        })
    }

    void copyResources() {
        project.copy(new Action<CopySpec>() {
            @Override
            void execute(CopySpec copySpec) {
                copySpec.into(destDir)
                copySpec.from(resources)
            }
        })
    }

    void generateSubprojects() {
        def allLibraries = getGeneratedLibraries()

        // Library projects are generated with source, tests and headers
        allLibraries.eachWithIndex { List<String> libraries, int libraryGroupIdx ->
            libraries.eachWithIndex { String subprojectName, int idx ->
                List<String> dependentProjects = []
                if (generateDeepHierarchy) {
                    // Each "layer" of libraries depends on the layer before it.
                    // This should generate a deep and wide dependency tree
                    if (libraryGroupIdx > 0) {
                        dependentProjects.addAll(allLibraries.get(libraryGroupIdx-1))
                    }
                }

                // Depend on all projects after this one.
                // With the number of projects we generate, this gives us a ~7-layer deep dependency graph
                // within a group of libraries
                dependentProjects.addAll(libraries.drop(idx+1))

                generateLibrarySubproject(subprojectName,
                        new File(projectTemplate, "lib.cpp"), new File(projectTemplate, "header.h"), new File(projectTemplate, "test_main.cpp"),
                        dependentProjects)
            }
        }

        // Executable projects are generated with just source
        def allExecutables = getGeneratedExecutables()
        allExecutables.eachWithIndex { List<String> executables, int executableGroupIdx ->
            executables.eachWithIndex { String subprojectName, int idx ->
                def dependentProjects = allLibraries.get(executableGroupIdx).flatten()
                generateExecutableSubproject(subprojectName, new File(projectTemplate, "main.cpp"), dependentProjects)
            }
        }
    }

    void generateLibrarySubproject(String generatedProjectName,
                                    File sourceFile, File headerFile, File testFile,
                                    List dependentProjects) {
        def buildFileProperties = [:]
        buildFileProperties.components = []
        for (int i=0; i<numberOfComponents; i++) {
            def generatedId = generatedProjectName + i
            def sourceProperties = [:]
            def includes = []
            sourceProperties.hasTests = true
            sourceProperties.generatedId = generatedId

            def deps = []
            if (i % 2 == 0) {
                // even components depend on another projects' component with index 2
                dependentProjects.each { dependentProject ->
                    def libName = "${dependentProject}2"
                    deps << new Dependency(":" + dependentProject, libName, 'static')
                    includes << "${libName}/header.h"
                }
            } else {
                // odd components depend on the component with index 2
                def libName = "${generatedProjectName}2"
                deps << new Dependency(":" + generatedProjectName, "${generatedProjectName}2", 'static')
                includes << "${libName}/header.h"
            }

            sourceProperties.includes = includes
            generateSources(generatedId, generatedProjectName, sourceFile, headerFile, testFile, sourceProperties)

            buildFileProperties.components << new Component(generatedId, NativeLibrarySpec.simpleName, deps)
        }

        generateBuildFile(generatedProjectName, projectTemplate, buildFileProperties)
    }


    void generateExecutableSubproject(String generatedProjectName,
                                      File sourceFile,
                                   List dependentProjects) {
        def buildFileProperties = [:]
        buildFileProperties.components = []
        for (int i=0; i<numberOfComponents; i++) {
            def generatedId = generatedProjectName + i
            def sourceProperties = [:]
            def includes = []
            sourceProperties.hasTests = false
            sourceProperties.generatedId = generatedId

            def deps = []
            dependentProjects.each { dependentProject ->
                def libName = "${dependentProject}2"
                deps << new Dependency(":" + dependentProject, libName, 'static')
                includes << "${libName}/header.h"
            }

            sourceProperties.includes = includes
            generateSources(generatedId, generatedProjectName, sourceFile, null, null, sourceProperties)

            buildFileProperties.components << new Component(generatedId, NativeExecutableSpec.simpleName, deps)
        }

        generateBuildFile(generatedProjectName, projectTemplate, buildFileProperties)
    }

    void generateBuildFile(String projectPath, File templateDir, Map<String, ?> properties) {
        project.copy(new Action<CopySpec>() {
            @Override
            void execute(CopySpec copySpec) {
                copySpec.from(new File(templateDir, "build.gradle"))
                copySpec.into(new File(destDir, projectPath))
                copySpec.expand(properties)
            }
        })
    }

    void generateSources(String generatedId, String projectPath, File sourceFile, File headerFile, File testFile, Map<String, ?> sourceProperties) {
        // TODO: Hack
        // For now, we do not actually use anything from our dependencies because this would require transitive dependencies
        sourceProperties.includes = []

        // TODO: Make it so we can have test sources for executables.
        // This doesn't work out of the box with just the google-test plugin (you need to exclude main.cpp).
        project.copy(new Action<CopySpec>() {
            @Override
            void execute(CopySpec copySpec) {
                copySpec.from(sourceFile) {
                    it.into "src/${generatedId}/cpp"
                }
                if (headerFile) {
                    copySpec.from(headerFile) {
                        it.into "src/${generatedId}/headers/${generatedId}"
                    }
                }
                if (testFile) {
                    copySpec.from(testFile) {
                        it.into "src/${generatedId}Test/cpp"
                    }
                }
                copySpec.into(new File(destDir, projectPath))
                copySpec.expand(sourceProperties)
            }
        })
    }

    // TODO: Make the number of projects configurable?

    // Generates "library" project names
    private static List<List<String>> getGeneratedLibraries() {
        return generateNames("lib")
    }

    // Generates "executable" project names
    private static List<List<String>> getGeneratedExecutables() {
        return generateNames("exe")
    }

    private static List<List<String>> generateNames(String prefix) {
        return ('A'..'Z').collect { letter ->
            (0..7).collect { number ->
                String.valueOf(prefix + letter + number)
            }
        } // 26*7 = 182
    }
}
