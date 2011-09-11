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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResolveIvyFactory implements Factory<Ivy> {
    private final IvyFactory ivyFactory;
    private final ResolverProvider resolverProvider;
    private final DependencyResolver internalRepository;
    private final Map<String, ModuleDescriptor> clientModuleRegistry;
    private final SettingsConverter settingsConverter;

    public ResolveIvyFactory(IvyFactory ivyFactory, ResolverProvider resolverProvider, SettingsConverter settingsConverter, DependencyResolver internalRepository, Map<String, ModuleDescriptor> clientModuleRegistry) {
        this.ivyFactory = ivyFactory;
        this.resolverProvider = resolverProvider;
        this.settingsConverter = settingsConverter;
        this.internalRepository = internalRepository;
        this.clientModuleRegistry = clientModuleRegistry;
    }

    public Ivy create() {
        List<DependencyResolver> resolvers = new ArrayList<DependencyResolver>();
        resolvers.add(internalRepository);
        resolvers.addAll(resolverProvider.getResolvers());
        return ivyFactory.createIvy(settingsConverter.convertForResolve(resolvers, clientModuleRegistry));
    }
}
