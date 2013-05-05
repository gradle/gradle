/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.messaging.serialize.DataStreamBackedSerializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class ModuleVersionIdentifierSerializer extends DataStreamBackedSerializer<ModuleVersionIdentifier> {
    @Override
    public void write(DataOutput dataOutput, ModuleVersionIdentifier value) throws IOException {
        dataOutput.writeUTF(value.getGroup());
        dataOutput.writeUTF(value.getName());
        dataOutput.writeUTF(value.getVersion());
    }

    @Override
    public ModuleVersionIdentifier read(DataInput dataInput) throws IOException {
        String group = dataInput.readUTF();
        String module = dataInput.readUTF();
        String version = dataInput.readUTF();
        return DefaultModuleVersionIdentifier.newId(group, module, version);
    }
}
