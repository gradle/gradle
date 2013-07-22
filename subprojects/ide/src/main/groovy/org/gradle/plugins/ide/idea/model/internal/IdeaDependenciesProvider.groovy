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

package org.gradle.plugins.ide.idea.model.internal

import org.gradle.api.Project
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor

class IdeaDependenciesProvider {

    private final IdeDependenciesExtractor dependenciesExtractor = new IdeDependenciesExtractor()
    Closure getPath;

    Set<org.gradle.plugins.ide.idea.model.Dependency> provide(IdeaModule ideaModule) {
        getPath = { File file -> file? ideaModule.pathFactory.path(file) : null }

        Set result = new LinkedHashSet()
        ideaModule.singleEntryLibraries.each { scope, files ->
            files.each {
                if (it && it.isDirectory()) {
                    result << new SingleEntryModuleLibrary(getPath(it), scope)
                }
            }
        }

        ideaModule.scopes.each { scopeName, scopeMap ->
            result.addAll(getModuleLibraries(ideaModule, scopeName, scopeMap))
            result.addAll(getModules(ideaModule.project, scopeName, scopeMap))
            result
        }

        return result
    }

    protected Set getModules(Project project, String scopeName, Map scopeMap) {
        if (!scopeMap) {
            return []
        }
        return dependenciesExtractor.extractProjectDependencies(scopeMap.plus, scopeMap.minus).collect {
                new ModuleDependencyBuilder().create(it.project, scopeName)
        }
    }

    protected Set getModuleLibraries(IdeaModule ideaModule, String scopeName, Map scopeMap) {
        if (!scopeMap) {
            return []
        }

        LinkedHashSet moduleLibraries = []

        if (!ideaModule.offline) {
            def repoFileDependencies = dependenciesExtractor.extractRepoFileDependencies(
                    ideaModule.project.configurations, scopeMap.plus, scopeMap.minus, 
                    ideaModule.downloadSources, ideaModule.downloadJavadoc)

            repoFileDependencies.each {
                def library = new SingleEntryModuleLibrary(
                        getPath(it.file), getPath(it.javadocFile), getPath(it.sourceFile), scopeName)
                library.moduleVersion = it.id
                moduleLibraries << library
            }
        }

        dependenciesExtractor.extractLocalFileDependencies(scopeMap.plus, scopeMap.minus).each {
            moduleLibraries << new SingleEntryModuleLibrary(getPath(it.file), scopeName)
        }
        moduleLibraries
    }
}
