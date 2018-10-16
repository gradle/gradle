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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
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

    private final BiMap<String, Integer> descriptions = HashBiMap.create();

    public ComponentSelectionReason read(Decoder decoder) throws IOException {
        List<ComponentSelectionDescriptor> descriptions = readDescriptions(decoder);
        return VersionSelectionReasons.of(descriptions);
    }

    private List<ComponentSelectionDescriptor> readDescriptions(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        ImmutableList.Builder<ComponentSelectionDescriptor> builder = new ImmutableList.Builder<ComponentSelectionDescriptor>();
        for (int i = 0; i < size; i++) {
            ComponentSelectionCause cause = ComponentSelectionCause.values()[decoder.readByte()];
            String desc = readDescriptionText(decoder);
            String defaultReason = cause.getDefaultReason();
            if (desc.equals(defaultReason)) {
                builder.add(new DefaultComponentSelectionDescriptor(cause));
            } else {
                builder.add(new DefaultComponentSelectionDescriptor(cause, Describables.of(desc)));
            }

        }
        return builder.build();
    }

    private String readDescriptionText(Decoder decoder) throws IOException {
        boolean alreadyKnown = decoder.readBoolean();
        if (alreadyKnown) {
            return descriptions.inverse().get(decoder.readSmallInt());
        } else {
            String description = decoder.readString();
            descriptions.put(description, descriptions.size());
            return description;
        }
    }

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
        Integer index = descriptions.get(description);
        encoder.writeBoolean(index != null); // already known custom reason
        if (index == null) {
            index = descriptions.size();
            descriptions.put(description, index);
            encoder.writeString(description);
        } else {
            encoder.writeSmallInt(index);
        }
    }

    public void reset() {
        descriptions.clear();
    }
}
