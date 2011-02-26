/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.eclipse.model.internal

import org.apache.commons.io.FilenameUtils
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.plugins.eclipse.EclipseWtpComponent
import org.gradle.plugins.eclipse.model.*

/**
 * @author Hans Dockter
 */
class WtpComponentFactory {
    void configure(EclipseWtpComponent eclipseComponent, WtpComponent component) {
        def entries = getEntriesFromSourceDirs(eclipseComponent)
        entries.addAll(eclipseComponent.resources)
        entries.addAll(eclipseComponent.properties)
        entries.addAll(getEntriesFromConfigurations(eclipseComponent))

        component.configure(eclipseComponent.deployName, eclipseComponent.contextPath, entries)
    }

    private List getEntriesFromSourceDirs(EclipseWtpComponent eclipseComponent) {
        eclipseComponent.sourceDirs.findAll { it.isDirectory() }.collect { dir ->
            new WbResource("/WEB-INF/classes", eclipseComponent.project.relativePath(dir))
        }
    }

    private List getEntriesFromConfigurations(EclipseWtpComponent eclipseComponent) {
        (getEntriesFromProjectDependencies(eclipseComponent) as List) + (getEntriesFromLibraries(eclipseComponent) as List)
    }

    // must include transitive project dependencies
    private Set getEntriesFromProjectDependencies(EclipseWtpComponent eclipseComponent) {
        def dependencies = getDependencies(eclipseComponent.plusConfigurations, eclipseComponent.minusConfigurations,
                { it instanceof org.gradle.api.artifacts.ProjectDependency })

        def projects = dependencies*.dependencyProject

        def allProjects = [] as LinkedHashSet
        allProjects.addAll(projects)
        projects.each { collectDependedUponProjects(it, allProjects) }

        allProjects.collect { project ->
            new WbDependentModule("/WEB-INF/lib", "module:/resource/" + project.name + "/" + project.name)
        }
    }

    // TODO: might have to search all class paths of all source sets for project dependendencies, not just runtime configuration
    private void collectDependedUponProjects(org.gradle.api.Project project, LinkedHashSet result) {
        def runtimeConfig = project.configurations.findByName("runtime")
        if (runtimeConfig) {
            def projectDeps = runtimeConfig.getAllDependencies(org.gradle.api.artifacts.ProjectDependency)
            def dependedUponProjects = projectDeps*.dependencyProject
            result.addAll(dependedUponProjects)
            for (dependedUponProject in dependedUponProjects) {
                collectDependedUponProjects(dependedUponProject, result)
            }
        }
    }

    // must NOT include transitive library dependencies
    private Set getEntriesFromLibraries(EclipseWtpComponent eclipseComponent) {
        Set declaredDependencies = getDependencies(eclipseComponent.plusConfigurations, eclipseComponent.minusConfigurations,
                { it instanceof ExternalDependency})

        Set libFiles = eclipseComponent.project.configurations.detachedConfiguration((declaredDependencies as Dependency[])).files +
                getSelfResolvingFiles(getDependencies(eclipseComponent.plusConfigurations, eclipseComponent.minusConfigurations,
                        { it instanceof SelfResolvingDependency && !(it instanceof org.gradle.api.artifacts.ProjectDependency)}))

        libFiles.collect { file ->
            createWbDependentModuleEntry(file, eclipseComponent.variables)
        }
    }

    private LinkedHashSet getSelfResolvingFiles(LinkedHashSet<SelfResolvingDependency> dependencies) {
        dependencies.collect { it.resolve() }.flatten()
    }

    private WbDependentModule createWbDependentModuleEntry(File file, Map<String, File> variables) {
        def usedVariableEntry = variables.find { name, value -> file.canonicalPath.startsWith(value.canonicalPath) }
        def handleSnippet
        if (usedVariableEntry) {
            handleSnippet = "var/$usedVariableEntry.key/${file.canonicalPath.substring(usedVariableEntry.value.canonicalPath.length())}"
        } else {
            handleSnippet = "lib/${file.canonicalPath}"
        }
        handleSnippet = FilenameUtils.separatorsToUnix(handleSnippet)
        return new WbDependentModule('/WEB-INF/lib', "module:/classpath/$handleSnippet")
    }

    private LinkedHashSet getDependencies(Set plusConfigurations, Set minusConfigurations, Closure filter) {
        def declaredDependencies = new LinkedHashSet()
        plusConfigurations.each { configuration ->
            declaredDependencies.addAll(configuration.allDependencies.findAll(filter))
        }
        minusConfigurations.each { configuration ->
            declaredDependencies.removeAll(configuration.allDependencies.findAll(filter))
        }
        return declaredDependencies
    }
}
