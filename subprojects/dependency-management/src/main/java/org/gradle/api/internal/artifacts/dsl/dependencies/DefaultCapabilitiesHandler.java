/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.dsl.CapabilityHandler;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.notations.ModuleIdentifierNotationConverter;
import org.gradle.internal.component.external.model.CapabilityDescriptor;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;

import java.util.Collection;
import java.util.Map;

public class DefaultCapabilitiesHandler implements CapabilitiesHandlerInternal {
    private final Map<String, DefaultCapability> capabilities = Maps.newHashMap();
    private final Multimap<ModuleIdentifier, DefaultCapability> moduleToCapabilities = LinkedHashMultimap.create();
    private final NotationParser<Object, ModuleIdentifier> notationParser;

    public DefaultCapabilitiesHandler(ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this.notationParser = parser(moduleIdentifierFactory);
    }

    @Override
    public void capability(String identifier, Action<? super CapabilityHandler> configureAction) {
        DefaultCapability capability = capabilities.get(identifier);
        if (capability == null) {
            capability = new DefaultCapability(notationParser, identifier);
            capabilities.put(identifier, capability);
        }
        configureAction.execute(capability);
        for (ModuleIdentifier moduleIdentifier : capability.getProvidedBy()) {
            moduleToCapabilities.put(moduleIdentifier, capability);
        }
    }

    @Override
    public void recordCapabilities(ModuleIdentifier module, Multimap<String, ModuleIdentifier> capabilityToModules) {
        Collection<DefaultCapability> capabilities = moduleToCapabilities.get(module);
        for (DefaultCapability capability : capabilities) {
            capabilityToModules.putAll(capability.getCapabilityId(), capability.getProvidedBy());
        }
    }

    @Override
    public ModuleIdentifier getPreferred(String id) {
        DefaultCapability capability = capabilities.get(id);
        if (capability != null) {
            return capability.getPrefer();
        }
        return null;
    }

    @Override
    public boolean hasCapabilities() {
        return !capabilities.isEmpty();
    }

    @Override
    public Collection<? extends CapabilityInternal> getCapabilities(ModuleIdentifier module) {
        return moduleToCapabilities.get(module);
    }

    @Override
    public CapabilityInternal getCapability(String name) {
        return capabilities.get(name);
    }

    @Override
    public ImmutableList<? extends CapabilityDescriptor> listCapabilities() {
        Collection<DefaultCapability> values = capabilities.values();
        if (values.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<CapabilityDescriptor> view = new ImmutableList.Builder<CapabilityDescriptor>();
        for (final DefaultCapability value : values) {
            view.add(value.toCapabilityDescriptor());
        }
        return view.build();
    }


    private static NotationParser<Object, ModuleIdentifier> parser(ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        return NotationParserBuilder
            .toType(ModuleIdentifier.class)
            .converter(new ModuleIdentifierNotationConverter(moduleIdentifierFactory))
            .toComposite();
    }

}
