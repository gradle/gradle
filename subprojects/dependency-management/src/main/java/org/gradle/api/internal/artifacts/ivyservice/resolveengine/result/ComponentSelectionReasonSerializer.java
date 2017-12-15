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
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons.*;

@NotThreadSafe
public class ComponentSelectionReasonSerializer implements Serializer<ComponentSelectionReason> {

    private static final BiMap<Byte, ComponentSelectionReasonInternal> REASONS = HashBiMap.create(7);

    private final BiMap<String, Integer> customReasons = HashBiMap.create();

    private OperationType lastOperationType = OperationType.read;

    static {
        REASONS.put((byte) 1, REQUESTED);
        REASONS.put((byte) 2, ROOT);
        REASONS.put((byte) 3, FORCED);
        REASONS.put((byte) 4, CONFLICT_RESOLUTION);
        REASONS.put((byte) 5, SELECTED_BY_RULE);
        REASONS.put((byte) 6, CONFLICT_RESOLUTION_BY_RULE);
        REASONS.put((byte) 7, COMPOSITE_BUILD);
        // update HashBiMap's expectedSize when adding new REASONS
    }

    public ComponentSelectionReason read(Decoder decoder) throws IOException {
        prepareForOperation(OperationType.read);
        byte id = decoder.readByte();
        ComponentSelectionReasonInternal out = REASONS.get(id);
        if (out == null) {
            throw new IllegalArgumentException("Unable to find selection reason with id: " + id);
        }
        if (!decoder.readBoolean()) {
            String reason = readCustomReason(decoder);
            out = out.withReason(reason);
        }
        return out;
    }

    private String readCustomReason(Decoder decoder) throws IOException {
        boolean alreadyKnown = decoder.readBoolean();
        if (alreadyKnown) {
            return customReasons.inverse().get(decoder.readSmallInt());
        } else {
            String description = decoder.readString();
            customReasons.put(description, customReasons.size());
            return description;
        }
    }

    public void write(Encoder encoder, ComponentSelectionReason value) throws IOException {
        prepareForOperation(OperationType.write);
        ComponentSelectionReason key = baseReason(value);
        Byte id = REASONS.inverse().get(key);
        if (id == null) {
            throw new IllegalArgumentException("Unknown selection reason: " + value);
        }
        encoder.writeByte(id);
        if (key == value) {
            encoder.writeBoolean(true);
        } else {
            writeDescription(encoder, value.getDescription());
        }
    }

    private void writeDescription(Encoder encoder, String description) throws IOException {
        encoder.writeBoolean(false); // non standard reason
        Integer index = customReasons.get(description);
        encoder.writeBoolean(index != null); // already known custom reason
        if (index == null) {
            index = customReasons.size();
            customReasons.put(description, index);
            encoder.writeString(description);
        } else {
            encoder.writeSmallInt(index);
        }
    }

    private static ComponentSelectionReason baseReason(ComponentSelectionReason value) {
        if (value == null) {
            return null;
        }
        if (value.isExpected()) {
            if (ROOT.getDescription().equals(value.getDescription())) {
                return ROOT;
            }
            return REQUESTED;
        }
        if (value.isForced()) {
            return FORCED;
        }
        if (value.isConflictResolution()) {
            if (value.isSelectedByRule()) {
                return CONFLICT_RESOLUTION_BY_RULE;
            }
            return CONFLICT_RESOLUTION;
        }
        if (value.isSelectedByRule()) {
            return SELECTED_BY_RULE;
        }
        if (value.isCompositeSubstitution()) {
            return COMPOSITE_BUILD;
        }
        return value;
    }

    /**
     * This serializer assumes that we are using it alternatively for writes, then reads, in cycles.
     * After each cycle completed, state has to be reset.
     *
     * @param operationType the current operation type
     */
    private void prepareForOperation(OperationType operationType) {
        if (operationType != lastOperationType) {
            customReasons.clear();
            lastOperationType = operationType;
        }
    }

    private enum OperationType {
        read,
        write
    }
}
