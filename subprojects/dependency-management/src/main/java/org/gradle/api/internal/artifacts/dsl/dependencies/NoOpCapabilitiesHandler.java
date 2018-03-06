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
import com.google.common.collect.Multimap;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.dsl.CapabilityHandler;
import org.gradle.internal.component.external.model.CapabilityDescriptor;

import java.util.Collection;
import java.util.Collections;

public class NoOpCapabilitiesHandler implements CapabilitiesHandlerInternal {
    public final static CapabilitiesHandlerInternal INSTANCE = new NoOpCapabilitiesHandler();

    @Override
    public void recordCapabilities(ModuleIdentifier module, Multimap<String, ModuleIdentifier> capabilityToModules) {
    }

    @Override
    public ModuleIdentifier getPreferred(String capability) {
        return null;
    }

    @Override
    public boolean hasCapabilities() {
        return false;
    }

    @Override
    public Collection<? extends CapabilityInternal> getCapabilities(ModuleIdentifier module) {
        return Collections.emptyList();
    }

    @Override
    public CapabilityInternal getCapability(String name) {
        return null;
    }

    @Override
    public ImmutableList<? extends CapabilityDescriptor> listCapabilities() {
        return ImmutableList.of();
    }

    @Override
    public void capability(String identifier, Action<? super CapabilityHandler> configureAction) {
    }
}
