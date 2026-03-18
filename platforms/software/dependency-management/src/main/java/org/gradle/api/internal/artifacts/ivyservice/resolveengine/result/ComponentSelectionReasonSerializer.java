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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;
import java.util.List;

/**
 * A thread-safe and reusable serializer for {@link ComponentSelectionReason} if and only if the passed in
 * {@link ComponentSelectionDescriptorFactory} is thread-safe and reusable.
 */
public class ComponentSelectionReasonSerializer implements Serializer<ComponentSelectionReasonInternal> {

    private final ComponentSelectionDescriptorSerializer componentSelectionDescriptorSerializer;

    public ComponentSelectionReasonSerializer(ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory) {
        this.componentSelectionDescriptorSerializer = new ComponentSelectionDescriptorSerializer(componentSelectionDescriptorFactory);
    }

    @Override
    public ComponentSelectionReasonInternal read(Decoder decoder) throws IOException {
        ImmutableList<ComponentSelectionDescriptorInternal> descriptions = readDescriptions(decoder);
        return new ComponentSelectionReasons.DefaultComponentSelectionReason(descriptions);
    }

    private ImmutableList<ComponentSelectionDescriptorInternal> readDescriptions(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        ImmutableList.Builder<ComponentSelectionDescriptorInternal> builder = ImmutableList.builderWithExpectedSize(size);
        for (int i = 0; i < size; i++) {
            builder.add(componentSelectionDescriptorSerializer.read(decoder));
        }
        return builder.build();
    }

    @Override
    public void write(Encoder encoder, ComponentSelectionReasonInternal value) throws IOException {
        List<? extends ComponentSelectionDescriptorInternal> descriptions = value.getDescriptions();
        encoder.writeSmallInt(descriptions.size());
        for (ComponentSelectionDescriptorInternal description : descriptions) {
            componentSelectionDescriptorSerializer.write(encoder, description);
        }
    }
}
