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

import java.io.IOException;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons.*;

public class ComponentSelectionReasonSerializer implements Serializer<ComponentSelectionReason> {

    private static final BiMap<Byte, ComponentSelectionReason> REASONS = HashBiMap.create(7);

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
        byte id = decoder.readByte();
        ComponentSelectionReason out = REASONS.get(id);
        if (out == null) {
            throw new IllegalArgumentException("Unable to find selection reason with id: " + id);
        }
        return out;
    }

    public void write(Encoder encoder, ComponentSelectionReason value) throws IOException {
        Byte id = REASONS.inverse().get(value);
        if (id == null) {
            throw new IllegalArgumentException("Unknown selection reason: " + value);
        }
        encoder.writeByte(id);
    }
}
