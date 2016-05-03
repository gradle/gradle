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
import org.gradle.api.tasks.ScalaRuntime
import org.gradle.language.scala.ScalaPlatform
import org.gradle.language.scala.internal.DefaultScalaPlatform
import org.gradle.language.scala.plugins.ScalaLanguagePlugin
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
            def useScalaSdk = ideaTargetVersion == null || ideaTargetVersion >= IDEA_VERSION_WHEN_SCALA_SDK_WAS_INTRODUCED
            def scalaProjects = findProjectsApplyingIdeaAndScalaPlugins()
            Map<String, ProjectLibrary> scalaCompilerLibraries = [:]
            rootProject.ideaProject.doFirst {
                if (scalaProjects.size() > 0) {
                    scalaCompilerLibraries = resolveScalaCompilerLibraries(scalaProjects, useScalaSdk)
                    declareUniqueProjectLibraries(scalaCompilerLibraries.values() as Set)
                }
            }

            rootProject.configure(scalaProjects) { Project project ->
                idea.module.iml.withXml { XmlProvider xmlProvider ->
                    if (useScalaSdk) {
                        declareScalaSdk(scalaCompilerLibraries[project.path], xmlProvider.asNode())
                    } else {
                        declareScalaFacet(scalaCompilerLibraries[project.path], xmlProvider.asNode())
                    }
                }
            }
        }
    }

    private Map<String, ProjectLibrary> resolveScalaCompilerLibraries(Collection<Project> scalaProjects, boolean useScalaSdk) {
        def scalaCompilerLibraries = [:]

        for (scalaProject in scalaProjects) {
            IdeaModule ideaModule = scalaProject.idea.module

            // could make resolveDependencies() cache its result for later use by GenerateIdeaModule
            def dependencies = ideaModule.resolveDependencies()
            def moduleLibraries = dependencies.findAll { it instanceof ModuleLibrary }
            def filePaths = moduleLibraries.collectMany { it.classes.findAll { it instanceof FilePath } }
            def files = filePaths.collect { it.file }

            ProjectLibrary library = createScalaSdkLibrary(scalaProject, files, useScalaSdk, ideaModule)
            if (library) {
                def duplicate = scalaCompilerLibraries.values().find { it == library }
                scalaCompilerLibraries[scalaProject.path] = duplicate ?: library
            }
        }

        return scalaCompilerLibraries
    }

    private ProjectLibrary createScalaSdkLibrary(Project scalaProject, List files, boolean useScalaSdk, IdeaModule ideaModule) {
        def runtime = scalaProject.getExtensions().findByType(ScalaRuntime)
        if (runtime) {
            // Old Scala Plugin does not specify a ScalaPlatform
            def scalaClasspath = runtime.inferScalaClasspath(files)
            def compilerJar = runtime.findScalaJar(scalaClasspath, "compiler")
            def scalaPlatform = new DefaultScalaPlatform()
            if (compilerJar != null) {
                scalaPlatform = new DefaultScalaPlatform(runtime.getScalaVersion(compilerJar))
            }
            return createScalaSdkFromPlatform(scalaPlatform, scalaClasspath, useScalaSdk)
        } else if (ideaModule.scalaPlatform) {
            // TODO: Wrong, using the full classpath of the application
            return createScalaSdkFromPlatform(ideaModule.scalaPlatform, scalaProject.files(files), useScalaSdk)
        } else {
            // One of the Scala plugins is applied, but ScalaRuntime extension is missing or the ScalaPlatform is undefined.
            // we can't create a Scala SDK without either one
            return null
        }
    }

    private ProjectLibrary createScalaSdkFromPlatform(ScalaPlatform platform, FileCollection scalaClasspath, boolean useScalaSdk) {
        def version = platform.scalaVersion
        def library = useScalaSdk ? createScalaSdkLibrary("scala-sdk-$version", scalaClasspath) : createProjectLibrary("scala-compiler-$version", scalaClasspath)
        library
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

    private void declareScalaSdk(ProjectLibrary scalaSdkLibrary, Node iml) {
        // only define a Scala SDK for a module if we could create a scalaSdkLibrary
        if (scalaSdkLibrary) {
            def newModuleRootManager = iml.component.find { it.@name == "NewModuleRootManager" }
            if (!newModuleRootManager) {
                newModuleRootManager = iml.appendNode("component", [name: "NewModuleRootManager"])
            }

            def sdkLibrary = newModuleRootManager.orderEntry.find { it.@name == scalaSdkLibrary.name }
            if (!sdkLibrary) {
                newModuleRootManager.appendNode("orderEntry", [type: "library", name: scalaSdkLibrary.name, level: "project"])
            }
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
            it.plugins.hasPlugin(IdeaPlugin) && (it.plugins.hasPlugin(ScalaBasePlugin) || it.plugins.hasPlugin(ScalaLanguagePlugin))
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
