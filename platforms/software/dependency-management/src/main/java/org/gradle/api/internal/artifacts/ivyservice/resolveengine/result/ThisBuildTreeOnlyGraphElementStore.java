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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Stores references to graph elements (components and variants) that are
 * referenced in serialized resolution results.
 */
@ServiceScope(Scope.BuildTree.class)
public class ThisBuildTreeOnlyGraphElementStore {

    private final Long2ObjectMap<ComponentGraphResolveState> components = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());
    private final Long2ObjectMap<VariantGraphResolveState> variants = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());

    /**
     * Stores a reference to the given component if it does not already exist,
     * returning an ID that can be used to retrieve the component later.
     */
    public long storeComponentReference(ComponentGraphResolveState component) {
        if (component.isAdHoc()) {
            throw new IllegalArgumentException("Cannot store adhoc component in this build tree only store.");
        }

        long instanceId = component.getInstanceId();
        components.putIfAbsent(instanceId, component);
        return instanceId;
    }

    /**
     * Returns the component with the given ID originally stored by
     * {@link #storeComponentReference(ComponentGraphResolveState)}.
     *
     * @throws IllegalStateException if no component with the given ID was stored.
     */
    public ComponentGraphResolveState getComponent(long componentReferenceId) {
        ComponentGraphResolveState componentGraphResolveState = components.get(componentReferenceId);
        if (componentGraphResolveState == null) {
            throw new IllegalStateException("No component with id " + componentReferenceId + " found.");
        }
        return componentGraphResolveState;
    }

    /**
     * Stores a reference to the given variant if it does not already exist,
     * returning an ID that can be used to retrieve the variant later.
     */
    public long storeVariantReference(VariantGraphResolveState variant) {
        long instanceId = variant.getInstanceId();
        variants.putIfAbsent(instanceId, variant);
        return instanceId;
    }

    /**
     * Returns the variant with the given ID originally stored by
     * {@link #storeVariantReference(VariantGraphResolveState)}.
     *
     * @throws IllegalStateException if no variant with the given ID was stored.
     */
    public VariantGraphResolveState getVariant(long variantReferenceId) {
        VariantGraphResolveState variantGraphResolveState = variants.get(variantReferenceId);
        if (variantGraphResolveState == null) {
            throw new IllegalStateException("No variant with id " + variantReferenceId + " found.");
        }
        return variantGraphResolveState;
    }

}
