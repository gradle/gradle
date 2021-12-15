/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;

public class ComponentSelectionDescriptorSerializer implements Serializer<ComponentSelectionDescriptor> {

    private final ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory;

    public ComponentSelectionDescriptorSerializer(ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory) {
        this.componentSelectionDescriptorFactory = componentSelectionDescriptorFactory;
    }

    @Override
    public ComponentSelectionDescriptor read(Decoder decoder) throws IOException {
        ComponentSelectionCause cause = ComponentSelectionCause.values()[decoder.readByte()];
        String desc = decoder.readString();
        String defaultReason = cause.getDefaultReason();
        if (desc.equals(defaultReason)) {
            return componentSelectionDescriptorFactory.newDescriptor(cause);
        }
        return componentSelectionDescriptorFactory.newDescriptor(cause, desc);
    }

    @Override
    public void write(Encoder encoder, ComponentSelectionDescriptor value) throws IOException {
        encoder.writeByte((byte) value.getCause().ordinal());
        encoder.writeString(value.getDescription());
    }
}
