/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal;

import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

public class TransportableActionExecutionSpecSerializer implements Serializer<TransportableActionExecutionSpec> {
    private final Serializer<ClassLoaderStructure> classLoaderStructureSerializer = new ClassLoaderStructureSerializer();

    @Override
    public void write(Encoder encoder, TransportableActionExecutionSpec spec) throws Exception {
        encoder.writeString(spec.getDisplayName());
        encoder.writeString(spec.getImplementationClassName());
        encoder.writeInt(spec.getSerializedParameters().length);
        encoder.writeBytes(spec.getSerializedParameters());
        classLoaderStructureSerializer.write(encoder, spec.getClassLoaderStructure());
    }

    @Override
    public TransportableActionExecutionSpec read(Decoder decoder) throws Exception {
        String displayName = decoder.readString();
        String implementationClassName = decoder.readString();
        int parametersSize = decoder.readInt();
        byte[] serializedParameters = new byte[parametersSize];
        decoder.readBytes(serializedParameters);
        ClassLoaderStructure classLoaderStructure = classLoaderStructureSerializer.read(decoder);
        return new TransportableActionExecutionSpec(displayName, implementationClassName, serializedParameters, classLoaderStructure);
    }
}
