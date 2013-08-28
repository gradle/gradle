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

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.result.ModuleVersionSelectionReason;
import org.gradle.api.internal.artifacts.ModuleVersionSelectorSerializer;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.Encoder;

import java.io.IOException;
import java.util.Map;

public class InternalDependencyResultSerializer {
    private final static byte SUCCESSFUL = 0;
    private final static byte FAILED = 1;
    private final ModuleVersionSelectorSerializer moduleVersionSelectorSerializer = new ModuleVersionSelectorSerializer();
    private final ModuleVersionSelectionReasonSerializer moduleVersionSelectionReasonSerializer = new ModuleVersionSelectionReasonSerializer();
    private final ModuleVersionSelectionSerializer moduleVersionSelectionSerializer = new ModuleVersionSelectionSerializer();

    public InternalDependencyResult read(Decoder decoder, Map<ModuleVersionSelector, ModuleVersionResolveException> failures) throws IOException {
        ModuleVersionSelector requested = moduleVersionSelectorSerializer.read(decoder);
        ModuleVersionSelectionReason reason = moduleVersionSelectionReasonSerializer.read(decoder);
        byte resultByte = decoder.readByte();
        if (resultByte == SUCCESSFUL) {
            ModuleVersionSelection selected = moduleVersionSelectionSerializer.read(decoder);
            return new DefaultInternalDependencyResult(requested, selected, reason, null);
        } else if (resultByte == FAILED) {
            ModuleVersionResolveException failure = failures.get(requested);
            return new DefaultInternalDependencyResult(requested, null, reason, failure);
        } else {
            throw new IllegalArgumentException("Unknown result byte: " + resultByte);
        }
    }

    public void write(Encoder encoder, InternalDependencyResult value) throws IOException {
        moduleVersionSelectorSerializer.write(encoder, value.getRequested());
        moduleVersionSelectionReasonSerializer.write(encoder, value.getReason());
        if (value.getFailure() == null) {
            encoder.writeByte(SUCCESSFUL);
            moduleVersionSelectionSerializer.write(encoder, value.getSelected());
        } else {
            encoder.writeByte(FAILED);
        }
    }
}
