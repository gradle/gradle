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

import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.internal.Describables;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.util.List;

@NotThreadSafe
public class ComponentSelectionReasonSerializer implements Serializer<ComponentSelectionReason> {

    @Override
    public ComponentSelectionReason read(Decoder decoder) throws IOException {
        ComponentSelectionDescriptor[] descriptions = readDescriptions(decoder);
        return ComponentSelectionReasons.of(descriptions);
    }

    private ComponentSelectionDescriptor[] readDescriptions(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        ComponentSelectionDescriptor[] descriptors = new ComponentSelectionDescriptor[size];
        for (int i = 0; i < size; i++) {
            ComponentSelectionCause cause = ComponentSelectionCause.values()[decoder.readByte()];
            String desc = readDescriptionText(decoder);
            String defaultReason = cause.getDefaultReason();
            if (desc.equals(defaultReason)) {
                descriptors[i] = new DefaultComponentSelectionDescriptor(cause);
            } else {
                descriptors[i] = new DefaultComponentSelectionDescriptor(cause, Describables.of(desc));
            }

        }
        return descriptors;
    }

    private String readDescriptionText(Decoder decoder) throws IOException {
        return decoder.readString();
    }

    @Override
    public void write(Encoder encoder, ComponentSelectionReason value) throws IOException {
        List<ComponentSelectionDescriptor> descriptions = value.getDescriptions();
        encoder.writeSmallInt(descriptions.size());
        for (ComponentSelectionDescriptor description : descriptions) {
            writeDescription(encoder, description);
        }
    }

    private void writeDescription(Encoder encoder, ComponentSelectionDescriptor description) throws IOException {
        encoder.writeByte((byte) description.getCause().ordinal());
        writeDescriptionText(encoder, description.getDescription());
    }

    private void writeDescriptionText(Encoder encoder, String description) throws IOException {
        encoder.writeString(description);
    }

}
