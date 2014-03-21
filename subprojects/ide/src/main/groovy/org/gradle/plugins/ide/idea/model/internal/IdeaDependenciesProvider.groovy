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

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap
import org.gradle.api.artifacts.Configuration
import org.gradle.plugins.ide.idea.model.Dependency
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor
import org.gradle.plugins.ide.internal.resolver.model.IdeDependencyKey
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency
import org.gradle.tooling.model.idea.IdeaDependency

class IdeaDependenciesProvider {

    private final IdeDependenciesExtractor dependenciesExtractor
    Closure getPath;

    /**
     * List of mappings used to assign IDEA classpath scope to project dependencies.
     *
     * Applied in order: if a dependency is found in all listed configurations it is provided as
     * a dependency in given scope(s).
     */
    Map<GeneratedIdeaScope, List<IdeaScopeMappingRule>> scopeMappings = new EnumMap<>(GeneratedIdeaScope)

    IdeaDependenciesProvider() {
        this(new IdeDependenciesExtractor())
    }

    private IdeaDependenciesProvider(IdeDependenciesExtractor dependenciesExtractor) {
        this.dependenciesExtractor = dependenciesExtractor
        scopeMappings.put(GeneratedIdeaScope.PROVIDED_TEST, [new IdeaScopeMappingRule(['providedRuntime', 'test'])])
        scopeMappings.put(GeneratedIdeaScope.PROVIDED,
                [new IdeaScopeMappingRule(['providedCompile']), new IdeaScopeMappingRule(['providedRuntime'])])
        scopeMappings.put(GeneratedIdeaScope.COMPILE, [new IdeaScopeMappingRule(['compile'])])
        scopeMappings.put(GeneratedIdeaScope.RUNTIME_TEST, [new IdeaScopeMappingRule(['testCompile', 'runtime'])])
        scopeMappings.put(GeneratedIdeaScope.RUNTIME, [new IdeaScopeMappingRule(['runtime'])])
        scopeMappings.put(GeneratedIdeaScope.TEST,
                [new IdeaScopeMappingRule(['testCompile']), new IdeaScopeMappingRule(['testRuntime'])])
    }

    Set<Dependency> provide(IdeaModule ideaModule) {
        getPath = { File file -> file? ideaModule.pathFactory.path(file) : null }

        Set<Dependency> result = new LinkedHashSet<Dependency>()
        ideaModule.singleEntryLibraries.each { scope, files ->
            files.each {
                if (it && it.isDirectory()) {
                    result << new SingleEntryModuleLibrary(getPath(it), scope)
                }
            }
        }
        result.addAll(provideFromScopeRuleMappings(ideaModule))
        result
    }

    private Set<Dependency> provideFromScopeRuleMappings(IdeaModule ideaModule) {
        Multimap<IdeDependencyKey<?, Dependency>, String> dependencyToConfigurations = LinkedHashMultimap.create()
        for (Configuration configuration : ideaModule.project.configurations) {
            if (!isMappedToIdeaScope(configuration, ideaModule)) {
                continue
            }
            // project dependencies
            Collection<IdeProjectDependency> ideProjectDependencies =
                    dependenciesExtractor.extractProjectDependencies(ideaModule.project, [configuration], [])
            for (IdeProjectDependency ideProjectDependency : ideProjectDependencies) {
                IdeDependencyKey<?, Dependency> key = IdeDependencyKey.forProjectDependency(
                        ideProjectDependency,
                        new IdeDependencyKey.DependencyBuilder<IdeProjectDependency, Dependency>() {
                            @Override
                            Dependency buildDependency(IdeProjectDependency dependency, String scope) {
                                return new ModuleDependencyBuilder().create(dependency.project, scope)
                            }});
                dependencyToConfigurations.put(key, configuration.name)
            }
            // repository dependencies
            if (!ideaModule.offline) {
                Collection<IdeExtendedRepoFileDependency> ideRepoFileDependencies = dependenciesExtractor.extractRepoFileDependencies(
                            ideaModule.project.dependencies, [configuration], [], ideaModule.downloadSources, ideaModule.downloadJavadoc);
                for (IdeExtendedRepoFileDependency ideRepoFileDependency : ideRepoFileDependencies) {
                    IdeDependencyKey<?, Dependency> key = IdeDependencyKey.forRepoFileDependency(
                            ideRepoFileDependency,
                            new IdeDependencyKey.DependencyBuilder<IdeExtendedRepoFileDependency, Dependency>() {
                                @Override
                                Dependency buildDependency(IdeExtendedRepoFileDependency dependency, String scope) {
                                    def library = new SingleEntryModuleLibrary(
                                            getPath(dependency.file), getPath(dependency.javadocFile), getPath(dependency.sourceFile), scope)
                                    library.moduleVersion = dependency.id
                                    return library
                                }});
                    dependencyToConfigurations.put(key, configuration.name)
                }
            }
            // file dependencies
            Collection<IdeLocalFileDependency> ideLocalFileDependencies =
                    dependenciesExtractor.extractLocalFileDependencies([configuration], []);
            for (IdeLocalFileDependency fileDependency : ideLocalFileDependencies) {
                IdeDependencyKey<?, Dependency> key = IdeDependencyKey.forLocalFileDependency(
                        fileDependency,
                        new IdeDependencyKey.DependencyBuilder<IdeLocalFileDependency, Dependency>() {
                            @Override
                            Dependency buildDependency(IdeLocalFileDependency dependency, String scope) {
                                return new SingleEntryModuleLibrary(getPath(dependency.file), scope)
                            }});
                dependencyToConfigurations.put(key, configuration.name)
            }
        }

        Set<IdeaDependency> dependencies = new LinkedHashSet<IdeaDependency>()
        for (GeneratedIdeaScope scope : GeneratedIdeaScope.values()) {
            Map<String, Collection<Configuration>> plusMinusConfigurations = ideaModule.scopes.get(scope.name())
            Collection<Configuration> minusConfigurations = plusMinusConfigurations?.minus
            Collection<String> minusConfigurationNames = minusConfigurations != null ? minusConfigurations.collect { it.name } : []

            for (IdeaScopeMappingRule scopeMappingRule : scopeMappings.get(scope)) {
                Collection<IdeDependencyKey<?>> matchingDependencies =
                        extractDependencies(dependencyToConfigurations, scopeMappingRule.configurationNames, minusConfigurationNames)
                for (IdeDependencyKey<?, Dependency> dependencyKey : matchingDependencies) {
                    dependencies.addAll(scope.scopes.collect { dependencyKey.buildDependency(it) })
                }
            }
            if (plusMinusConfigurations && plusMinusConfigurations.plus) {
                for (Configuration plusConfiguration : plusMinusConfigurations.plus) {
                    Collection<IdeDependencyKey<?>> matchingDependencies =
                            extractDependencies(dependencyToConfigurations, [plusConfiguration.name], minusConfigurationNames)
                    for (IdeDependencyKey<?, Dependency> dependencyKey : matchingDependencies) {
                        dependencies.addAll(scope.scopes.collect { dependencyKey.buildDependency(it) })
                    }
                }
            }
        }
        dependencies
    }

    boolean isMappedToIdeaScope(Configuration configuration, IdeaModule ideaModule) {
        if (scopeMappings.values().flatten().find { IdeaScopeMappingRule it -> it.configurationNames.contains(configuration.name) }) {
            return true
        }
        for (Map<String, Collection<Configuration>> scopeMap : ideaModule.scopes.values()) {
            if (scopeMap.values().flatten().find { Configuration it -> it.equals(configuration) }) {
                return true
            }
        }
        false
    }

    /** Looks for dependencies contained in all configurations to remove them from multimap and return as result. */
    def extractDependencies(Multimap<IdeDependencyKey<?, Dependency>, String> dependenciesToConfigs,
                            Collection<String> configurations, Collection<String> minusConfigurations) {
        List<IdeDependencyKey<?, Dependency>> deps = new ArrayList<>()
        for (IdeDependencyKey<?> dependencyKey : dependenciesToConfigs.keySet()) {
            if (dependenciesToConfigs.get(dependencyKey).containsAll(configurations)) {
                def isInMinus = false
                for (String minusConfiguration : minusConfigurations) {
                    if (dependenciesToConfigs.get(dependencyKey).contains(minusConfiguration)) {
                        isInMinus = true
                        break
                    }
                }
                if (!isInMinus) {
                    deps.add(dependencyKey)
                }
            }
        }
        for (IdeDependencyKey<?, Dependency> key : deps) {
            dependenciesToConfigs.removeAll(key)
        }
        deps
    }
}
