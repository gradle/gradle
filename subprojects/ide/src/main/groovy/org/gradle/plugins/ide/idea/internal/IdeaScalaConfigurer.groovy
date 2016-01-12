/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.plugins.ide.idea.internal

import org.gradle.api.GradleScriptException
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.api.resources.MissingResourceException
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.*
import org.gradle.util.VersionNumber

class IdeaScalaConfigurer {
    // More information: http://blog.jetbrains.com/scala/2014/10/30/scala-plugin-update-for-intellij-idea-14-rc-is-out/
    private static final VersionNumber IDEA_VERSION_WHEN_SCALA_SDK_WAS_INTRODUCED = VersionNumber.version(14);

    private final Project rootProject

    IdeaScalaConfigurer(Project rootProject) {
        this.rootProject = rootProject
    }

    void configure() {
        rootProject.gradle.projectsEvaluated {
            def ideaTargetVersion = findIdeaTargetVersion()
            def scalaProjects = findProjectsApplyingIdeaAndScalaPlugins()
            def scalaCompilerLibrary
            rootProject.ideaProject.doFirst {
                if (scalaProjects.size() > 0) {
                    def scalaProject = findScalaProjectWithHighestLibraryVersion(scalaProjects)
                    if (ideaTargetVersion != null && ideaTargetVersion < IDEA_VERSION_WHEN_SCALA_SDK_WAS_INTRODUCED) {
                        scalaCompilerLibrary = declareScalaFacetOnRootProject(scalaProject)
                    } else {
                        declareScalaSdkOnRootProject(scalaProject)
                    }
                }
            }

            rootProject.configure(scalaProjects) { Project project ->
                idea.module.iml.withXml { XmlProvider xmlProvider ->
                    if (ideaTargetVersion != null && ideaTargetVersion < IDEA_VERSION_WHEN_SCALA_SDK_WAS_INTRODUCED) {
                        declareScalaFacet(scalaCompilerLibrary, xmlProvider.asNode())
                    } else {
                        declareScalaSdk(xmlProvider.asNode())
                    }
                }
            }
        }
    }

    private Project findScalaProjectWithHighestLibraryVersion(Collection<Project> scalaProjects) {
        def scalaProjectWithHighestLibraryVersion = null
        def highestVersion = 0

        for (scalaProject in scalaProjects) {
            def IdeaModule ideaModule = scalaProject.idea.module

            // could make resolveDependencies() cache its result for later use by GenerateIdeaModule
            def dependencies = ideaModule.resolveDependencies()
            def moduleLibraries = dependencies.findAll { it instanceof ModuleLibrary }

            for (moduleLibrary in moduleLibraries) {
                def moduleVersion = moduleLibrary.moduleVersion
                if (moduleVersion != null) {
                    def moduleName = moduleVersion.name
                    if (moduleName == "scala-library") {
                        def version = moduleVersion.version.replaceAll("[^0-9]", "").replace(".", "").toInteger()
                        if (version > highestVersion) {
                            scalaProjectWithHighestLibraryVersion = scalaProject
                            highestVersion = version
                        }
                    }
                }
            }
        }

        if (scalaProjectWithHighestLibraryVersion == null) {
            throw new MissingResourceException("Unable to find Scala project. Most likely this is due to a missing 'scala-library' dependency." +
                " Make sure your maven repos are accessible and that your dependencies exists in the local Gradle cache.")
        }

        return scalaProjectWithHighestLibraryVersion
    }

    private FileCollection resolveScalaClasspath(Project scalaProject) {
        def IdeaModule ideaModule = scalaProject.idea.module

        // could make resolveDependencies() cache its result for later use by GenerateIdeaModule
        def dependencies = ideaModule.resolveDependencies()
        def moduleLibraries = dependencies.findAll { it instanceof ModuleLibrary }
        def filePaths = moduleLibraries.collectMany { it.classes.findAll { it instanceof FilePath } }
        def files = filePaths.collect { it.file }

        return scalaProject.scalaRuntime.inferScalaClasspath(files)
    }

    private void addLibraryToRootProjectLibraries(ProjectLibrary library) {
        def existingLibraries = rootProject.idea.project.projectLibraries
        def suffix = 1
        for (existingLibrary in existingLibraries) {
            if (existingLibrary.name == library.name) {
                existingLibrary.name = "$existingLibrary.name-${suffix++}"
            }
        }
        existingLibraries << library
    }

    private void declareScalaSdkOnRootProject(Project scalaProject) {
        def scalaClasspath = resolveScalaClasspath(scalaProject)
        def library = createScalaSdkLibrary("scala-sdk", scalaClasspath)
        addLibraryToRootProjectLibraries(library)
    }

    private void declareScalaSdk(Node iml) {
        def newModuleRootManager = iml.component.find { it.@name == "NewModuleRootManager" }
        if (!newModuleRootManager) {
            newModuleRootManager = iml.appendNode("component", [name: "NewModuleRootManager"])
        }

        def sdkLibrary = newModuleRootManager.orderEntry.find { it.@name == "scala-sdk" }
        if (!sdkLibrary) {
            newModuleRootManager.appendNode("orderEntry", [type: "library", name: "scala-sdk", level: "project"])
        }
    }

    private ProjectLibrary declareScalaFacetOnRootProject(Project scalaProject) {
        def scalaClasspath = resolveScalaClasspath(scalaProject)
        def compilerJar = scalaProject.scalaRuntime.findScalaJar(scalaClasspath, "compiler")
        def version = compilerJar == null ? "?" : scalaProject.scalaRuntime.getScalaVersion(compilerJar)
        def library = createProjectLibrary("scala-compiler-$version", scalaClasspath)
        addLibraryToRootProjectLibraries(library)
        return library
    }

    private void declareScalaFacet(ProjectLibrary scalaCompilerLibrary, Node iml) {
        def facetManager = iml.component.find { it.@name == "FacetManager" }
        if (!facetManager) {
            facetManager = iml.appendNode("component", [name: "FacetManager"])
        }

        def scalaFacet = facetManager.facet.find { it.@type == "scala" }
        if (!scalaFacet) {
            scalaFacet = facetManager.appendNode("facet", [type: "scala", name: "Scala"])
        }

        def configuration = scalaFacet.configuration[0]
        if (!configuration) {
            configuration = scalaFacet.appendNode("configuration")
        }

        def libraryLevel = configuration.option.find { it.@name == "compilerLibraryLevel" }
        if (!libraryLevel) {
            libraryLevel = configuration.appendNode("option", [name: "compilerLibraryLevel"])
        }
        libraryLevel.@value = "Project"

        def libraryName = configuration.option.find { it.@name == "compilerLibraryName" }
        if (!libraryName) {
            libraryName = configuration.appendNode("option", [name: "compilerLibraryName"])
        }

        libraryName.@value = scalaCompilerLibrary.name
    }

    private Collection<Project> findProjectsApplyingIdeaAndScalaPlugins() {
        rootProject.allprojects.findAll {
            it.plugins.hasPlugin(IdeaPlugin) && it.plugins.hasPlugin(ScalaBasePlugin)
        }
    }

    private VersionNumber findIdeaTargetVersion() {
        def targetVersion = null
        def targetVersionString = rootProject.extensions.getByType(IdeaModel).targetVersion

        if (targetVersionString != null) {
            targetVersion = VersionNumber.parse(targetVersionString)
            if (targetVersion == VersionNumber.UNKNOWN) {
                throw new GradleScriptException("String '$targetVersionString' is not a valid value for IdeaModel.targetVersion.")
            }
        }

        return targetVersion
    }

    private ProjectLibrary createProjectLibrary(String name, Iterable<File> jars) {
        new ProjectLibrary(name: name, classes: jars as Set)
    }

    private ProjectLibrary createScalaSdkLibrary(String name, Iterable<File> jars) {
        new ProjectLibrary(name: name, type: 'Scala', compilerClasspath: jars as Set)
    }
}
