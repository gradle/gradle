/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphComponent;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

/**
 * A {@link ComponentResultSerializer} that determines whether to fully serialize a component
 * depending on if it is adhoc or not.
 */
public class AdhocHandlingComponentResultSerializer implements ComponentResultSerializer {

    private final ThisBuildTreeOnlyComponentResultSerializer thisBuildTreeOnlyComponentResultSerializer;
    private final CompleteComponentResultSerializer completeComponentResultSerializer;

    public AdhocHandlingComponentResultSerializer(
        ThisBuildTreeOnlyComponentResultSerializer thisBuildTreeOnlyComponentResultSerializer,
        CompleteComponentResultSerializer completeComponentResultSerializer
    ) {
        this.thisBuildTreeOnlyComponentResultSerializer = thisBuildTreeOnlyComponentResultSerializer;
        this.completeComponentResultSerializer = completeComponentResultSerializer;
    }

    @Override
    public void writeComponentResult(Encoder encoder, ResolvedGraphComponent component, boolean includeAllSelectableVariantResults) throws Exception {
        boolean adHoc = component.getResolveState().isAdHoc();
        encoder.writeBoolean(adHoc);
        getSerializer(adHoc).writeComponentResult(encoder, component, includeAllSelectableVariantResults);
    }

    @Override
    public void readComponentResult(Decoder decoder, ResolvedComponentVisitor visitor) throws Exception {
        boolean adHoc = decoder.readBoolean();
        getSerializer(adHoc).readComponentResult(decoder, visitor);
    }

    private ComponentResultSerializer getSerializer(boolean adhoc) {
        if (adhoc) {
            // We do not want to hold a reference to adhoc components.
            // So, we use the complete serializer that writes all data and does not hold references to state.
            return completeComponentResultSerializer;
        } else {
            // We know non-adhoc components are likely to be used across many resolutions in the build.
            // For this reason, we reuse their serialized representations by holding references to their state.
            return thisBuildTreeOnlyComponentResultSerializer;
        }
    }
}
