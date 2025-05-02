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

package org.gradle.process.internal.worker.request;

import org.gradle.internal.serialize.DefaultSerializer;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.SerializerRegistry;

public class RequestSerializerRegistry {
    public static SerializerRegistry create(ClassLoader classLoader, RequestArgumentSerializers argumentSerializers) {
        SerializerRegistry registry = new DefaultSerializerRegistry(false);
        registry.register(Request.class, new RequestSerializer(argumentSerializers.getSerializer(classLoader), false));
        return registry;
    }

    public static SerializerRegistry createDiscardRequestArg() {
        SerializerRegistry registry = new DefaultSerializerRegistry(false);
        registry.register(Request.class, new RequestSerializer(new DefaultSerializer<>(), true));
        return registry;
    }
}
