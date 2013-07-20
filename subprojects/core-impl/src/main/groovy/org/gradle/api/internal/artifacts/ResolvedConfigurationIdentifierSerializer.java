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

public class ResolvedConfigurationIdentifierSerializer extends DataStreamBackedSerializer<ResolvedConfigurationIdentifier> {

    private final ModuleVersionIdentifierSerializer idSerializer = new ModuleVersionIdentifierSerializer();

    @Override
    public ResolvedConfigurationIdentifier read(DataInput dataInput) throws IOException {
        ModuleVersionIdentifier id = idSerializer.read(dataInput);
        String configuration = dataInput.readUTF();
        return new ResolvedConfigurationIdentifier(id, configuration);
    }

    @Override
    public void write(DataOutput dataOutput, ResolvedConfigurationIdentifier value) throws IOException {
        idSerializer.write(dataOutput, value.getId());
        dataOutput.writeUTF(value.getConfiguration());
    }
}
