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

package org.gradle.tooling.internal.provider;

import org.gradle.internal.UncheckedException;
import org.gradle.messaging.remote.internal.Message;
import org.gradle.util.ClasspathUtil;
import org.gradle.util.GUtil;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;

public class PayloadSerializer {
    private final ModelClassLoaderRegistry classLoaderRegistry;

    public PayloadSerializer(ModelClassLoaderRegistry classLoaderRegistry) {
        this.classLoaderRegistry = classLoaderRegistry;
    }

    public SerializedPayload serialize(Object payload) {
        List<URL> classpath = payload == null ? Collections.<URL>emptyList() : ClasspathUtil.getClasspath(payload.getClass().getClassLoader());
        byte[] serializedModel = GUtil.serialize(payload);
        return new SerializedPayload(classpath, serializedModel);
    }

    public Object deserialize(SerializedPayload model) {
        ClassLoader classLoader = classLoaderRegistry.getClassLoaderFor(model.getClassPath());
        try {
            return Message.receive(new ByteArrayInputStream(model.getSerializedModel()), classLoader);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
