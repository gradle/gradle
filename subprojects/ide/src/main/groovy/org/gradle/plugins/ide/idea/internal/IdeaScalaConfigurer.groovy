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

import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.FilePath
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.ModuleLibrary
import org.gradle.plugins.ide.idea.model.ProjectLibrary

class IdeaScalaConfigurer {
    private final Project rootProject

    IdeaScalaConfigurer(Project rootProject) {
        this.rootProject = rootProject
    }

    void configure() {
        rootProject.gradle.projectsEvaluated {
            def scalaProjects = findProjectsApplyingIdeaAndScalaPlugins()
            Map<String, ProjectLibrary> scalaCompilerLibraries = [:]

            rootProject.ideaProject.doFirst {
                scalaCompilerLibraries = resolveScalaCompilerLibraries(scalaProjects)
                declareUniqueProjectLibraries(scalaCompilerLibraries.values() as Set)
            }

            rootProject.configure(scalaProjects) { org.gradle.api.Project prj ->
                idea.module.iml.withXml { XmlProvider xmlProvider ->
                    declareScalaFacet(scalaCompilerLibraries[prj.path], xmlProvider.asNode())
                }
            }
        }
    }

    private Map<String, ProjectLibrary> resolveScalaCompilerLibraries(Collection<Project> scalaProjects) {
        def scalaCompilerLibraries = [:]

        for (scalaProject in scalaProjects) {
            IdeaModule ideaModule = scalaProject.idea.module

            // could make resolveDependencies() cache its result for later use by GenerateIdeaModule
            def dependencies = ideaModule.resolveDependencies()
            def moduleLibraries = dependencies.findAll { it instanceof ModuleLibrary }
            def filePaths = moduleLibraries.collectMany { it.classes.findAll { it instanceof FilePath } }
            def files = filePaths.collect { it.file }

            def runtime = scalaProject.scalaRuntime
            def scalaClasspath = runtime.inferScalaClasspath(files)
            def compilerJar = runtime.findScalaJar(scalaClasspath, "compiler")
            def version = compilerJar == null ? "?" : runtime.getScalaVersion(compilerJar)
            def library = createProjectLibrary("scala-compiler-$version", scalaClasspath)
            def duplicate = scalaCompilerLibraries.values().find { it.classes == library.classes }
            scalaCompilerLibraries[scalaProject.path] = duplicate ?: library
        }

        return scalaCompilerLibraries
    }

    private void declareUniqueProjectLibraries(Set<ProjectLibrary> projectLibraries) {
        def existingLibraries = rootProject.idea.project.projectLibraries
        def newLibraries = projectLibraries - existingLibraries
        for (newLibrary in newLibraries) {
            def originalName = newLibrary.name
            def suffix = 1
            while (existingLibraries.find { it.name == newLibrary.name }) {
                newLibrary.name = "$originalName-${suffix++}"
            }
            existingLibraries << newLibrary
        }
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

    private ProjectLibrary createProjectLibrary(String name, Iterable<File> jars) {
        new ProjectLibrary(name: name, classes: jars as Set)
    }
}
