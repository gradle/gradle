/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.internal.serialize.ClassLoaderObjectInputStream;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;

@NonNullApi
public class ReplacingObjectInputStream extends ClassLoaderObjectInputStream {
    private final LazyPayloadSerializerContainer payloadSerializer;

    public ReplacingObjectInputStream(InputStream inputSteam, LazyPayloadSerializerContainer payloadSerializer, ClassLoader classLoader) throws IOException {
        super(inputSteam, classLoader);
        this.payloadSerializer = payloadSerializer;
        enableResolveObject(true);
    }

    @Override
    @Nullable
    protected final Object resolveObject(Object obj) {
        if (obj instanceof StreamDataPlaceHolder) {
            return payloadSerializer.get().deserialize(((StreamDataPlaceHolder) obj).getData());
        }
        return obj;
    }
}
