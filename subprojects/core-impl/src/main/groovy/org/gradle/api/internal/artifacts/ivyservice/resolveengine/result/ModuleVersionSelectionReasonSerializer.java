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
import org.gradle.api.artifacts.result.ModuleVersionSelectionReason;
import org.gradle.messaging.serialize.DataStreamBackedSerializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons.*;

public class ModuleVersionSelectionReasonSerializer extends DataStreamBackedSerializer<ModuleVersionSelectionReason> {

    private static final BiMap<Short, ModuleVersionSelectionReason> reasons = HashBiMap.create(6);

    static {
        reasons.put((short) 1, REQUESTED);
        reasons.put((short) 2, ROOT);
        reasons.put((short) 3, FORCED);
        reasons.put((short) 4, CONFLICT_RESOLUTION);
        reasons.put((short) 5, SELECTED_BY_RULE);
        reasons.put((short) 6, CONFLICT_RESOLUTION_BY_RULE);
    }

    public ModuleVersionSelectionReason read(DataInput dataInput) throws Exception {
        short id = dataInput.readShort();
        ModuleVersionSelectionReason out = reasons.get(id);
        if (out == null) {
            throw new IllegalArgumentException("Unable to find selection reason with id: " + id);
        }
        return out;
    }

    public void write(DataOutput dataOutput, ModuleVersionSelectionReason value) throws IOException {
        Short id = reasons.inverse().get(value);
        if (id == null) {
            throw new IllegalArgumentException("Unknown selection reason: " + value);
        }
        dataOutput.writeShort(id.intValue());
    }
}
