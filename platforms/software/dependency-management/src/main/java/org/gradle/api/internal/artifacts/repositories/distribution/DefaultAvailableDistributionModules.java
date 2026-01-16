/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.distribution;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.DependencyClassPathProvider;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.classpath.Module;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link AvailableDistributionModules}, which makes available
 * a pre-determined set of top-level modules, plus their transitive dependencies.
 */
public class DefaultAvailableDistributionModules implements AvailableDistributionModules {

    /**
     * All top-level modules we have decided to allow users to depend on. All modules in this list,
     * and all of their transitive dependencies, will be available to users via the Gradle
     * distribution repository.
     */
    private static final ImmutableList<String> EXPOSED_TOP_LEVEL_MODULES = ImmutableList.<String>builder()
        .addAll(DependencyClassPathProvider.GROOVY_MODULES)
        .build();

    private final ImmutableMap<ModuleIdentifier, AvailableModule> availableModules;

    @Inject
    public DefaultAvailableDistributionModules(
        ModuleRegistry moduleRegistry
    ) {
        this.availableModules = collectAvailableModules(moduleRegistry);
    }

    private static ImmutableMap<ModuleIdentifier, AvailableModule> collectAvailableModules(ModuleRegistry moduleRegistry) {
        Iterable<Module> topLevelModules = Iterables.transform(EXPOSED_TOP_LEVEL_MODULES, moduleRegistry::getModule);

        // Discover the transitive closure of all modules we will make available.
        List<Module> allAccessibleModules = moduleRegistry.getRuntimeModules(topLevelModules);
        ImmutableMap.Builder<String, Module> builder = ImmutableMap.builderWithExpectedSize(allAccessibleModules.size());
        for (Module module : allAccessibleModules) {
            builder.put(module.getName(), module);
        }
        ImmutableMap<String, Module> modulesByName = builder.build();

        // Recursively build `AvailableModule` instances to expose.
        Map<String, AvailableModule> availableModulesByName = new HashMap<>(allAccessibleModules.size());
        for (String topLevelModuleName : EXPOSED_TOP_LEVEL_MODULES) {
            getOrCreateModule(topLevelModuleName, modulesByName, availableModulesByName);
        }

        // Map each `AvailableModule` instance by their alias ID.
        ImmutableMap.Builder<ModuleIdentifier, AvailableModule> availableModules = ImmutableMap.builderWithExpectedSize(availableModulesByName.size());
        for (AvailableModule module : availableModulesByName.values()) {
            availableModules.put(module.getId().getModuleIdentifier(), module);
        }

        return availableModules.build();
    }

    private static AvailableModule getOrCreateModule(
        String name,
        ImmutableMap<String, Module> modulesByName,
        Map<String, AvailableModule> availableModulesByName
    ) {
        AvailableModule module = availableModulesByName.get(name);
        if (module == null) {
            module = doCreateAvailableModule(name, modulesByName, availableModulesByName);
            availableModulesByName.put(name, module);
        }
        return module;
    }

    private static AvailableModule doCreateAvailableModule(
        String name,
        ImmutableMap<String, Module> modulesByName,
        Map<String, AvailableModule> availableModulesByName
    ) {
        Module module = modulesByName.get(name);
        Module.ModuleAlias alias = module.getAlias();
        if (alias == null) {
            throw new IllegalStateException("Module '" + name + "' has no alias");
        }
        ModuleComponentIdentifier id = toModuleVersionId(alias);

        List<String> dependencyNames = module.getDependencyNames();
        ImmutableList.Builder<AvailableModule> dependencies = ImmutableList.builderWithExpectedSize(dependencyNames.size());
        for (String dependencyName : dependencyNames) {
            dependencies.add(getOrCreateModule(dependencyName, modulesByName, availableModulesByName));
        }

        return new DefaultAvailableModule(
            name,
            id,
            module.getImplementationClasspath(),
            dependencies.build()
        );
    }

    private static ModuleComponentIdentifier toModuleVersionId(Module.ModuleAlias alias) {
        ModuleIdentifier moduleId = DefaultModuleIdentifier.newId(alias.getGroup(), alias.getName());
        return DefaultModuleComponentIdentifier.newId(moduleId, alias.getVersion());
    }

    @Override
    public @Nullable AvailableModule getModule(ModuleComponentIdentifier id) {
        AvailableModule module = availableModules.get(id.getModuleIdentifier());
        if (module != null && module.getId().getVersion().equals(id.getVersion())) {
            return module;
        }

        return null;
    }

    @Override
    public @Nullable String getModuleVersion(ModuleIdentifier moduleId) {
        AvailableModule module = availableModules.get(moduleId);
        if (module != null) {
            return module.getId().getVersion();
        }

        return null;
    }

    /**
     * Exposed so we can verify in tests that the list of available modules does not change,
     * or that if it does, we are aware of the change.
     */
    @VisibleForTesting
    public ImmutableCollection<ModuleComponentIdentifier> getAvailableModules() {
        return availableModules.values().stream()
            .map(AvailableModule::getId)
            .collect(ImmutableList.toImmutableList());
    }

    @VisibleForTesting
    public ImmutableCollection<ModuleComponentIdentifier> getTopLevelModules() {
        return EXPOSED_TOP_LEVEL_MODULES.stream().map(x ->
            availableModules.values().stream()
                .filter(it -> it.getName().equals(x)).findFirst().get()
                .getId()
        ).collect(ImmutableList.toImmutableList());
    }

    private static class DefaultAvailableModule implements AvailableModule {

        private final String name;
        private final ModuleComponentIdentifier id;
        private final ClassPath classpath;
        private final List<AvailableModule> dependencies;

        public DefaultAvailableModule(
            String name,
            ModuleComponentIdentifier id,
            ClassPath classpath,
            List<AvailableModule> dependencies
        ) {
            this.name = name;
            this.id = id;
            this.classpath = classpath;
            this.dependencies = dependencies;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ModuleComponentIdentifier getId() {
            return id;
        }

        @Override
        public ClassPath getImplementationClasspath() {
            return classpath;
        }

        @Override
        public List<AvailableModule> getDependencies() {
            return dependencies;
        }

    }

}
