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

import java.io.*;
import java.util.Map;

public class InternalDependencyResultSerializer {

    private final static int SUCCESSFUL = 0;
    private final static int FAILED = 1;

    public InternalDependencyResult read(InputStream instr, Map<ModuleVersionSelector, ModuleVersionResolveException> failures) throws IOException {
        DataInputStream dataInput = new DataInputStream(instr);
        ModuleVersionSelector requested = new ModuleVersionSelectorSerializer().read((DataInput) dataInput);
        ModuleVersionSelectionReason reason = new ModuleVersionSelectionReasonSerializer().read((DataInput) dataInput);
        byte resultByte = dataInput.readByte();
        if (resultByte == SUCCESSFUL) {
            ModuleVersionSelection selected = new ModuleVersionSelectionSerializer().read((DataInput) dataInput);
            return new DefaultInternalDependencyResult(requested, selected, reason, null);
        } else if (resultByte == FAILED) {
            ModuleVersionResolveException failure = failures.get(requested);
            return new DefaultInternalDependencyResult(requested, null, reason, failure);
        } else {
            throw new IllegalArgumentException("Unknown result byte: " + resultByte);
        }
    }

    public void write(OutputStream outstr, InternalDependencyResult value) throws IOException {
        DataOutputStream dataOutput = new DataOutputStream(outstr);
        new ModuleVersionSelectorSerializer().write((DataOutput) dataOutput, value.getRequested());
        new ModuleVersionSelectionReasonSerializer().write((DataOutput) dataOutput, value.getReason());
        if (value.getFailure() == null) {dataOutput.writeByte(SUCCESSFUL);
            new ModuleVersionSelectionSerializer().write((DataOutput) dataOutput, value.getSelected());
        } else {
            dataOutput.writeByte(FAILED);
        }
    }
}
