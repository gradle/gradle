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

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphDependency;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.Map;

public class DependencyResultSerializer {
    private final static byte SUCCESSFUL = 0;
    private final static byte SUCCESSFUL_NOTHING_SELECTED = 1;
    private final static byte FAILED = 2;
    private final ComponentSelectionReasonSerializer componentSelectionReasonSerializer;

    public DependencyResultSerializer(ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory) {
        this.componentSelectionReasonSerializer = new ComponentSelectionReasonSerializer(componentSelectionDescriptorFactory);
    }

    public ResolvedGraphDependency read(Decoder decoder, Map<Long, ComponentSelector> selectors, Map<ComponentSelector, ModuleVersionResolveException> failures) throws IOException {
        long selectorId = decoder.readSmallLong();
        ComponentSelector requested = selectors.get(selectorId);
        boolean constraint = decoder.readBoolean();
        long fromVariant = decoder.readSmallLong();
        byte resultByte = decoder.readByte();
        if (resultByte == SUCCESSFUL) {
            long selectedId = decoder.readSmallLong();
            long selectedVariantId = decoder.readSmallLong();
            return new DetachedResolvedGraphDependency(requested, selectedId, null, null, constraint, fromVariant, selectedVariantId);
        } else if (resultByte == SUCCESSFUL_NOTHING_SELECTED) {
            long selectedId = decoder.readSmallLong();
            return new DetachedResolvedGraphDependency(requested, selectedId, null, null, constraint, fromVariant, null);
        } else if (resultByte == FAILED) {
            ComponentSelectionReason reason = componentSelectionReasonSerializer.read(decoder);
            ModuleVersionResolveException failure = failures.get(requested);
            return new DetachedResolvedGraphDependency(requested, null, reason, failure, constraint, fromVariant, null);
        } else {
            throw new IllegalArgumentException("Unknown result type: " + resultByte);
        }
    }

    public void write(Encoder encoder, DependencyGraphEdge value) throws IOException {
        encoder.writeSmallLong(value.getSelector().getResultId());
        encoder.writeBoolean(value.isConstraint());
        encoder.writeSmallLong(value.getFromVariant());
        if (value.getFailure() == null) {
            if (value.getSelectedVariant() != null) {
                encoder.writeByte(SUCCESSFUL);
                encoder.writeSmallLong(value.getSelected());
                encoder.writeSmallLong(value.getSelectedVariant());
            } else {
                encoder.writeByte(SUCCESSFUL_NOTHING_SELECTED);
                encoder.writeSmallLong(value.getSelected());
            }
        } else {
            encoder.writeByte(FAILED);
            componentSelectionReasonSerializer.write(encoder, value.getReason());
        }
    }
}
