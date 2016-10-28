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

package org.gradle.plugins.ide.idea.model.internal;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.composite.internal.CompositeBuildIdeProjectResolver;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugins.ide.idea.model.Dependency;
import org.gradle.plugins.ide.idea.model.FilePath;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary;
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor;
import org.gradle.plugins.ide.internal.resolver.model.IdeDependencyKey;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeLocalFileDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency;
import org.gradle.plugins.ide.internal.resolver.model.UnresolvedIdeRepoFileDependency;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IdeaDependenciesProvider {

    private final IdeDependenciesExtractor dependenciesExtractor;
    private final ModuleDependencyBuilder moduleDependencyBuilder;
    private Transformer<FilePath, File> getPath;

    /**
     * List of mappings used to assign IDEA classpath scope to project dependencies.
     *
     * Applied in order: if a dependency is found in all listed configurations it is provided as
     * a dependency in given scope(s).
     */
    private Map<GeneratedIdeaScope, List<IdeaScopeMappingRule>> scopeMappings = new EnumMap<GeneratedIdeaScope, List<IdeaScopeMappingRule>>(GeneratedIdeaScope.class);

    public IdeaDependenciesProvider(ServiceRegistry serviceRegistry) {
        this(new IdeDependenciesExtractor(), serviceRegistry);
    }

    IdeaDependenciesProvider(IdeDependenciesExtractor dependenciesExtractor, ServiceRegistry serviceRegistry) {
        this.dependenciesExtractor = dependenciesExtractor;
        scopeMappings.put(GeneratedIdeaScope.PROVIDED_TEST,
                Collections.singletonList(new IdeaScopeMappingRule("providedRuntime", "test")));
        scopeMappings.put(GeneratedIdeaScope.PROVIDED,
                Lists.newArrayList(new IdeaScopeMappingRule("providedCompile"), new IdeaScopeMappingRule("providedRuntime")));
        scopeMappings.put(GeneratedIdeaScope.COMPILE,
                Collections.singletonList(new IdeaScopeMappingRule("compile")));
        scopeMappings.put(GeneratedIdeaScope.RUNTIME_COMPILE_CLASSPATH,
                Collections.singletonList(new IdeaScopeMappingRule("compileClasspath", "runtime")));
        scopeMappings.put(GeneratedIdeaScope.RUNTIME_TEST_COMPILE_CLASSPATH,
                Collections.singletonList(new IdeaScopeMappingRule("compileClasspath", "testRuntime")));
        scopeMappings.put(GeneratedIdeaScope.RUNTIME_TEST,
                Collections.singletonList(new IdeaScopeMappingRule("testCompile", "runtime")));
        scopeMappings.put(GeneratedIdeaScope.RUNTIME,
                Collections.singletonList(new IdeaScopeMappingRule("runtime")));
        scopeMappings.put(GeneratedIdeaScope.TEST,
                Lists.newArrayList(new IdeaScopeMappingRule("testCompileClasspath"), new IdeaScopeMappingRule("testCompile"), new IdeaScopeMappingRule("testRuntime")));
        scopeMappings.put(GeneratedIdeaScope.COMPILE_CLASSPATH,
                Collections.singletonList(new IdeaScopeMappingRule("compileClasspath")));

        moduleDependencyBuilder = new ModuleDependencyBuilder(CompositeBuildIdeProjectResolver.from(serviceRegistry));
    }

    public Set<Dependency> provide(final IdeaModule ideaModule) {
        getPath = new Transformer<FilePath, File>() {
            @Override
            @Nullable
            public FilePath transform(File file) {
                return file != null ? ideaModule.getPathFactory().path(file) : null;
            }
        };

        Set<Dependency> result = new LinkedHashSet<Dependency>();
        if (ideaModule.getSingleEntryLibraries() != null) {
            for (Map.Entry<String, Iterable<File>> singleEntryLibrary : ideaModule.getSingleEntryLibraries().entrySet()) {
                String scope = singleEntryLibrary.getKey();
                for (File file : singleEntryLibrary.getValue()) {
                    if (file != null && file.isDirectory()) {
                        result.add(new SingleEntryModuleLibrary(getPath.transform(file), scope));
                    }
                }
            }
        }
        result.addAll(provideFromScopeRuleMappings(ideaModule));
        return result;
    }

    public Collection<UnresolvedIdeRepoFileDependency> getUnresolvedDependencies(IdeaModule ideaModule) {
        Set<UnresolvedIdeRepoFileDependency> usedUnresolvedDependencies = Sets.newTreeSet(new Comparator<UnresolvedIdeRepoFileDependency>() {
            @Override
            public int compare(UnresolvedIdeRepoFileDependency left, UnresolvedIdeRepoFileDependency right) {
                return left.getDisplayName().compareTo(right.getDisplayName());
            }
        });

        for (GeneratedIdeaScope scope : GeneratedIdeaScope.values()) {
            Map<String, Collection<Configuration>> plusMinusConfigurations = ideaModule.getScopes().get(scope.name());
            if (plusMinusConfigurations == null) {
                if (shouldProcessScope(scope, ideaModule.getScopes())) {
                    plusMinusConfigurations = Collections.emptyMap();
                } else {
                    continue;
                }
            }
            List<Configuration> plusConfigurations = plusMinusConfigurations.containsKey("plus")
                ? Lists.newArrayList(plusMinusConfigurations.get("plus"))
                : Lists.<Configuration>newArrayList();
            List<Configuration> minusConfigurations = plusMinusConfigurations.containsKey("minus")
                ? Lists.newArrayList(plusMinusConfigurations.get("minus"))
                : Lists.<Configuration>newArrayList();
            for (IdeaScopeMappingRule scopeMappingRule : scopeMappings.get(scope)) {
                for(Configuration configuration: ideaModule.getProject().getConfigurations()) {
                    if (scopeMappingRule.configurationNames.contains(configuration.getName())) {
                        plusConfigurations.add(configuration);
                    }
                }
            }
            usedUnresolvedDependencies.addAll(dependenciesExtractor.unresolvedExternalDependencies(plusConfigurations, minusConfigurations));
        }
        return usedUnresolvedDependencies;
    }

    private Set<Dependency> provideFromScopeRuleMappings(IdeaModule ideaModule) {
        Multimap<IdeDependencyKey<?, Dependency>, String> dependencyToConfigurations = LinkedHashMultimap.create();
        for (Configuration configuration : ideaConfigurations(ideaModule)) {
            // project dependencies
            Collection<IdeProjectDependency> ideProjectDependencies = dependenciesExtractor.extractProjectDependencies(
                    ideaModule.getProject(), Collections.singletonList(configuration), Collections.<Configuration>emptyList());
            for (IdeProjectDependency ideProjectDependency : ideProjectDependencies) {
                IdeDependencyKey<?, Dependency> key = IdeDependencyKey.forProjectDependency(
                        ideProjectDependency,
                        new IdeDependencyKey.DependencyBuilder<IdeProjectDependency, Dependency>() {
                            @Override
                            public Dependency buildDependency(IdeProjectDependency dependency, String scope) {
                                return moduleDependencyBuilder.create(dependency, scope);
                            }});
                dependencyToConfigurations.put(key, configuration.getName());
            }
            // repository dependencies
            if (!ideaModule.isOffline()) {
                Collection<IdeExtendedRepoFileDependency> ideRepoFileDependencies = dependenciesExtractor.extractRepoFileDependencies(
                        ideaModule.getProject().getDependencies(), Collections.singletonList(configuration), Collections.<Configuration>emptyList(),
                        ideaModule.isDownloadSources(), ideaModule.isDownloadJavadoc());
                for (IdeExtendedRepoFileDependency ideRepoFileDependency : ideRepoFileDependencies) {
                    IdeDependencyKey<?, Dependency> key = IdeDependencyKey.forRepoFileDependency(
                            ideRepoFileDependency,
                            new IdeDependencyKey.DependencyBuilder<IdeExtendedRepoFileDependency, Dependency>() {
                                @Override
                                public Dependency buildDependency(IdeExtendedRepoFileDependency dependency, String scope) {
                                    Set<FilePath> javadoc = CollectionUtils.collect(dependency.getJavadocFiles(), new LinkedHashSet<FilePath>(), getPath);
                                    Set<FilePath> source = CollectionUtils.collect(dependency.getSourceFiles(), new LinkedHashSet<FilePath>(), getPath);
                                    SingleEntryModuleLibrary library = new SingleEntryModuleLibrary(
                                            getPath.transform(dependency.getFile()), javadoc, source, scope);
                                    library.setModuleVersion(dependency.getId());
                                    return library;
                                }});
                    dependencyToConfigurations.put(key, configuration.getName());
                }
            }
            // file dependencies
            Collection<IdeLocalFileDependency> ideLocalFileDependencies = dependenciesExtractor.extractLocalFileDependencies(
                    Collections.singletonList(configuration), Collections.<Configuration>emptyList());
            for (IdeLocalFileDependency fileDependency : ideLocalFileDependencies) {
                IdeDependencyKey<?, Dependency> key = IdeDependencyKey.forLocalFileDependency(
                        fileDependency,
                        new IdeDependencyKey.DependencyBuilder<IdeLocalFileDependency, Dependency>() {
                            @Override
                            public Dependency buildDependency(IdeLocalFileDependency dependency, String scope) {
                                return new SingleEntryModuleLibrary(getPath.transform(dependency.getFile()), scope);
                            }});
                dependencyToConfigurations.put(key, configuration.getName());
            }
        }

        Set<Dependency> dependencies = new LinkedHashSet<Dependency>();
        for (GeneratedIdeaScope scope : GeneratedIdeaScope.values()) {
            Map<String, Collection<Configuration>> plusMinusConfigurations = ideaModule.getScopes().get(scope.name());
            if (plusMinusConfigurations == null) {
                if (shouldProcessScope(scope, ideaModule.getScopes())) {
                    plusMinusConfigurations = Collections.emptyMap();
                } else {
                    continue;
                }
            }
            Collection<Configuration> minusConfigurations = plusMinusConfigurations.get("minus");
            Collection<String> minusConfigurationNames = minusConfigurations != null
                    ? Lists.newArrayList(Iterables.transform(
                            minusConfigurations,
                            new Function<Configuration, String>() {
                                @Override
                                public String apply(Configuration configuration) {
                                    return configuration.getName();
                                }
                            }
                    ))
                    : Collections.<String>emptyList();

            for (IdeaScopeMappingRule scopeMappingRule : scopeMappings.get(scope)) {
                Collection<IdeDependencyKey<?, Dependency>> matchingDependencies =
                        extractDependencies(dependencyToConfigurations, scopeMappingRule.configurationNames, minusConfigurationNames);
                for (final IdeDependencyKey<?, Dependency> dependencyKey : matchingDependencies) {
                    dependencies.addAll(Lists.newArrayList(Iterables.transform(
                            scope.scopes,
                            scopeToDependency(dependencyKey))));
                }
            }
            if (plusMinusConfigurations.containsKey("plus")) {
                for (Configuration plusConfiguration : plusMinusConfigurations.get("plus")) {
                    Collection<IdeDependencyKey<?, Dependency>> matchingDependencies =
                            extractDependencies(dependencyToConfigurations, Collections.singletonList(plusConfiguration.getName()), minusConfigurationNames);
                    for (IdeDependencyKey<?, Dependency> dependencyKey : matchingDependencies) {
                        dependencies.addAll(Lists.newArrayList(Iterables.transform(
                                scope.scopes,
                                scopeToDependency(dependencyKey))));
                    }
                }
            }
        }
        return dependencies;
    }

    private boolean shouldProcessScope(GeneratedIdeaScope scope, Map<String, Map<String, Collection<Configuration>>> scopes) {
        // composite scopes are not present in IdeaModule.scopes - check their mapped scope names
        for (String scopeName : scope.scopes) {
            if (!scopes.containsKey(scopeName)) {
                return false;
            }
        }
        return true;
    }

    private static Function<String, Dependency> scopeToDependency(final IdeDependencyKey<?, Dependency> dependencyKey) {
        return new Function<String, Dependency>() {
            @Override
            @Nullable
            public Dependency apply(String s) {
                return dependencyKey.buildDependency(s);
            }
        };
    }

    private Iterable<Configuration> ideaConfigurations(final IdeaModule ideaModule) {
        Set<Configuration> configurations = Sets.newLinkedHashSet(ideaModule.getProject().getConfigurations());
        for (Map<String, Collection<Configuration>> scopeMap : ideaModule.getScopes().values()) {
            for (Configuration cfg : Iterables.concat(scopeMap.values())) {
                configurations.add(cfg);
            }
        }
        return Iterables.filter(
                configurations,
                new Predicate<Configuration>() {
                    @Override
                    public boolean apply(Configuration input) {
                        return isMappedToIdeaScope(input, ideaModule);
                    }
                });
    }

    private boolean isMappedToIdeaScope(final Configuration configuration, IdeaModule ideaModule) {
        Iterable<IdeaScopeMappingRule> rules = Iterables.concat(scopeMappings.values());
        boolean matchesRule = Iterables.any(rules, new Predicate<IdeaScopeMappingRule>() {
            @Override
            public boolean apply(IdeaScopeMappingRule ideaScopeMappingRule) {
                return ideaScopeMappingRule.configurationNames.contains(configuration.getName());
            }
        });
        if (matchesRule) {
            return true;
        }
        for (Map<String, Collection<Configuration>> scopeMap : ideaModule.getScopes().values()) {
            Iterable<Configuration> configurations = Iterables.concat(scopeMap.values());
            if (Iterables.any(configurations, Predicates.equalTo(configuration))) {
                return true;
            }
        }
        return false;
    }

    /** Looks for dependencies contained in all configurations to remove them from multimap and return as result. */
    private List<IdeDependencyKey<?, Dependency>> extractDependencies(Multimap<IdeDependencyKey<?, Dependency>, String> dependenciesToConfigs,
                            Collection<String> configurations, Collection<String> minusConfigurations) {
        List<IdeDependencyKey<?, Dependency>> deps = new ArrayList<IdeDependencyKey<?, Dependency>>();
        List<IdeDependencyKey<?, Dependency>> minusDeps = new ArrayList<IdeDependencyKey<?, Dependency>>();
        for (IdeDependencyKey<?, Dependency> dependencyKey : dependenciesToConfigs.keySet()) {
            if (dependenciesToConfigs.get(dependencyKey).containsAll(configurations)) {
                boolean isInMinus = false;
                for (String minusConfiguration : minusConfigurations) {
                    if (dependenciesToConfigs.get(dependencyKey).contains(minusConfiguration)) {
                        isInMinus = true;
                        break;
                    }
                }
                if (!isInMinus) {
                    deps.add(dependencyKey);
                } else {
                    minusDeps.add(dependencyKey);
                }
            }
        }
        for (IdeDependencyKey<?, Dependency> key : Iterables.concat(deps, minusDeps)) {
            dependenciesToConfigs.removeAll(key);
        }
        return deps;
    }
}
