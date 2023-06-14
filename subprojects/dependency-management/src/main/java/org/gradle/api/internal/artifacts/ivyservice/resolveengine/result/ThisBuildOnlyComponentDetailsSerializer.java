/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.util.List;

/**
 * A serializer used for resolution results that will be consumed from the same Gradle invocation that produces them.
 *
 * <p>Writes a reference to the {@link ComponentGraphResolveState} instance to build the result from, rather than persisting the associated data.</p>
 */
@ThreadSafe
public class ThisBuildOnlyComponentDetailsSerializer implements ComponentDetailsSerializer {
    private final Long2ObjectMap<ComponentGraphResolveState> components = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());

    @Override
    public void writeComponentDetails(ComponentGraphResolveState component, boolean requireAllVariants, Encoder encoder) throws IOException {
        long instanceId = component.getInstanceId();
        components.putIfAbsent(instanceId, component);
        encoder.writeSmallLong(instanceId);
        encoder.writeBoolean(requireAllVariants);
    }

    @Override
    public void readComponentDetails(Decoder decoder, ResolvedComponentVisitor visitor) throws IOException {
        long instanceId = decoder.readSmallLong();
        ComponentGraphResolveState component = components.get(instanceId);
        if (component == null) {
            throw new IllegalStateException("No component with id " + instanceId + " found.");
        }
        visitor.visitComponentDetails(component.getId(), component.getMetadata().getModuleVersionId());
        List<ResolvedVariantResult> availableVariants;
        if (decoder.readBoolean()) {
            // use all available variants
            availableVariants = component.getAllSelectableVariantResults();
        } else {
            availableVariants = ImmutableList.of();
        }
        visitor.visitComponentVariants(availableVariants);
    }
}
