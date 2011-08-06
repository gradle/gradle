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
import org.gradle.api.artifacts.Configuration
import org.gradle.plugins.ide.idea.model.FilePath
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.PathFactory
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor

/**
 * TODO SF - later do some clean up in this class.
 * For example remove unnecessary fields, pass configuration instead of scope name, etc.
 *
 * @author Szczepan Faber, created at: 4/1/11
 */
class IdeaDependenciesProvider {

    Project project
    Map<String, Map<String, Collection<Configuration>>> scopes
    boolean downloadSources
    boolean downloadJavadoc
    PathFactory pathFactory
    boolean offline

    private final IdeDependenciesExtractor dependenciesExtractor = new IdeDependenciesExtractor()

    Set<org.gradle.plugins.ide.idea.model.Dependency> provide(IdeaModule ideaModule, PathFactory pathFactory) {
        this.project = ideaModule.project
        this.scopes = ideaModule.scopes
        this.downloadSources = ideaModule.downloadSources
        this.downloadJavadoc = ideaModule.downloadJavadoc
        this.pathFactory = pathFactory
        this.offline = ideaModule.offline

        Set result = new LinkedHashSet()
        ideaModule.singleEntryLibraries.each { scope, files ->
            files.each {
                if (it && it.isDirectory()) {
                    result << new SingleEntryModuleLibrary(getPath(it), scope)
                }
            }
        }

        scopes.keySet().each { scope ->
            result.addAll(getModuleLibraries(scope))
            result.addAll(getModules(scope))
            result
        }

        return result
    }

    protected Set getModules(String scope) {
        def s = scopes[scope]
        if (s) {
            return dependenciesExtractor.extractProjectDependencies(s.plus, s.minus).collect {
                new ModuleDependencyBuilder().create(it.project, scope)
            }
        }
        return []
    }

    protected Set getModuleLibraries(String scope) {
        if (!scopes[scope]) { return [] }

        LinkedHashSet moduleLibraries = []

        if (!offline) {
            def repoFileDependencies = dependenciesExtractor.extractRepoFileDependencies(
                    project.configurations, scopes[scope].plus, scopes[scope].minus, downloadSources, downloadJavadoc)

            repoFileDependencies.each {
                moduleLibraries << new SingleEntryModuleLibrary(getPath(it.file), getPath(it.javadocFile), getPath(it.sourceFile), scope)
            }
        }

        dependenciesExtractor.extractLocalFileDependencies(scopes[scope].plus, scopes[scope].minus).each {
            moduleLibraries << new SingleEntryModuleLibrary(getPath(it.file), scope)
        }
        moduleLibraries
    }

    protected FilePath getPath(File file) {
        file? pathFactory.path(file) : null
    }
}
