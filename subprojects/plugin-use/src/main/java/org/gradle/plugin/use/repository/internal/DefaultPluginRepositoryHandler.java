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

package org.gradle.plugin.use.repository.internal;

import com.google.common.collect.Iterators;
import org.gradle.api.Action;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.dsl.PluginRepositoryHandler;
import org.gradle.api.internal.plugins.repositories.MavenPluginRepository;
import org.gradle.api.internal.plugins.repositories.PluginRepository;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class DefaultPluginRepositoryHandler implements PluginRepositoryHandler {

    private FileResolver fileResolver;
    private Factory<DependencyResolutionServices> dependencyResolutionServicesFactory;
    private VersionSelectorScheme versionSelectorScheme;
    private Instantiator instantiator;
    private Set<String> repoNames;
    private List<PluginRepository> repositories;

    public DefaultPluginRepositoryHandler(FileResolver fileResolver, Factory<DependencyResolutionServices> dependencyResolutionServicesFactory, VersionSelectorScheme versionSelectorScheme, Instantiator instantiator) {
        this.instantiator = instantiator;
        this.fileResolver = fileResolver;
        this.dependencyResolutionServicesFactory = dependencyResolutionServicesFactory;
        this.versionSelectorScheme = versionSelectorScheme;
        this.repoNames = new HashSet<String>();
        this.repositories = new ArrayList<PluginRepository>();
    }

    @Override
    public MavenPluginRepository maven(Action<? super MavenPluginRepository> configurationAction) {
        DefaultMavenPluginRepository mavenPluginRepository = instantiator.newInstance(
            DefaultMavenPluginRepository.class, fileResolver, dependencyResolutionServicesFactory.create(), versionSelectorScheme);
        configurationAction.execute(mavenPluginRepository);
        add(mavenPluginRepository);
        return mavenPluginRepository;
    }

    @Override
    public Iterator<PluginRepository> iterator() {
        return Iterators.unmodifiableIterator(repositories.iterator());
    }

    private void add(PluginRepository pluginRepository) {
        uniquifyName(pluginRepository);
        repositories.add(pluginRepository);
    }

    private void uniquifyName(PluginRepository pluginRepository) {
        String name = pluginRepository.getName();
        if (name == null) {
            name = "maven";
        }
        name = uniquifyName(name);
        pluginRepository.setName(name);
    }

    private String uniquifyName(String proposedName) {
        int attempt = 1;
        while (repoNames.contains(proposedName)) {
            attempt++;
            proposedName = String.format("%s%d", proposedName, attempt);
        }
        repoNames.add(proposedName);
        return proposedName;
    }
}
