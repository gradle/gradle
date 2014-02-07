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

package org.gradle.plugins.ide.internal

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.plugins.ide.internal.resolver.DefaultIdeDependencyResolver
import org.gradle.plugins.ide.internal.resolver.IdeDependencyResolver
import org.gradle.plugins.ide.internal.resolver.model.IdeRepoFileDependency

class JavadocAndSourcesDownloader {
    static final String SOURCES_DEPENDENCY_CLASSIFER = 'sources'
    static final String JAVADOC_DEPENDENCY_CLASSIFER = 'javadoc'
    IdeDependencyResolver ideDependencyResolver = new DefaultIdeDependencyResolver()

    private Map<String, File> sourceFiles
    private Map<String, File> javadocFiles

    JavadocAndSourcesDownloader(ConfigurationContainer confContainer, Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations,
                                boolean downloadSources, boolean downloadJavadoc) {
        if (!downloadJavadoc && !downloadSources) {
            return
        }

        if (downloadSources) {
            sourceFiles = determineFileMapping(confContainer, plusConfigurations, minusConfigurations, SOURCES_DEPENDENCY_CLASSIFER) { Configuration configuration, ConfigurationContainer configurationContainer ->
                ideDependencyResolver.getIdeSourceDependencies(configuration, configurationContainer)
            }
        }

        if (downloadJavadoc) {
            javadocFiles = determineFileMapping(confContainer, plusConfigurations, minusConfigurations, JAVADOC_DEPENDENCY_CLASSIFER) { Configuration configuration, ConfigurationContainer configurationContainer ->
                ideDependencyResolver.getIdeJavadocDependencies(configuration, configurationContainer)
            }
        }
    }

    File sourceFor(String name) {
        sourceFiles?.get(name)
    }

    File javadocFor(String name) {
        javadocFiles?.get(name)
    }

    private Map<String, File> determineFileMapping(ConfigurationContainer confContainer, Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations, String classifier, Closure closure) {
        Map<String, File> mappedSourceFiles = new HashMap<String, File>();

        for (plusConfiguration in plusConfigurations) {
            List<IdeRepoFileDependency> deps = closure(plusConfiguration, confContainer)
            mappedSourceFiles.putAll(mapFiles(deps, classifier))
        }

        for (minusConfiguration in minusConfigurations) {
            List<IdeRepoFileDependency> deps = closure(minusConfiguration, confContainer)
            mappedSourceFiles.keySet().removeAll(mapFiles(deps, classifier).keySet())
        }

        mappedSourceFiles
    }

    private Map<String, File> mapFiles(List<IdeRepoFileDependency> sourceFileDependencies, String classifier) {
        Map<String, File> mappedSourceFiles = new HashMap<String, File>();

        for(IdeRepoFileDependency dependency : sourceFileDependencies) {
            String key = dependency.file.name.replace("-${classifier}.jar", '.jar')
            mappedSourceFiles.put(key, dependency.file)
        }

        mappedSourceFiles
    }
}
