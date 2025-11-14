/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.provider.serialization;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.internal.Cast.uncheckedNonnullCast;

@ThreadSafe
@ServiceScope(Scope.UserHome.class)
public class PayloadSerializer {

    private final PayloadClassLoaderRegistry classLoaderRegistry;

    public PayloadSerializer(PayloadClassLoaderRegistry registry) {
        classLoaderRegistry = registry;
    }

    public SerializedPayload serialize(@Nullable Object payload) {
        if (payload == null) {
            return new SerializedPayload(null, Collections.emptyList());
        }
        final SerializeMap map = classLoaderRegistry.newSerializeSession();
        try {
            StreamByteBuffer buffer = new StreamByteBuffer();
            try (ObjectOutputStream out = new PayloadSerializerObjectOutputStream(buffer.getOutputStream(), map)) {
                out.writeObject(payload);
            }
            Map<Short, ClassLoaderDetails> classLoaders = new HashMap<Short, ClassLoaderDetails>();
            map.collectClassLoaderDefinitions(classLoaders);
            return new SerializedPayload(classLoaders, buffer.readAsListOfByteArrays());
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public @Nullable Object deserialize(SerializedPayload payload) {
        if (payload.getSerializedModel().isEmpty()) {
            return null;
        }
        try {
            return new PayloadSerializerObjectInputStream(
                    StreamByteBuffer.of(payload.getSerializedModel()).getInputStream(),
                    getClass().getClassLoader(),
                    uncheckedNonnullCast(payload.getHeader()),
                    classLoaderRegistry.newDeserializeSession()
            ).readObject();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
