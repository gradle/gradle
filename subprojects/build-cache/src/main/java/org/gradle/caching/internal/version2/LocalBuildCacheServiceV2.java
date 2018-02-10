/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.caching.internal.version2;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.NonNullApi;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.io.IoAction;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@NonNullApi
public interface LocalBuildCacheServiceV2 {
    @Nullable
    Result getResult(HashCode key);
    void getContent(HashCode key, ContentProcessor contentProcessor);

    void putFile(HashCode key, IoAction<OutputStream> writer);
    void putManifest(HashCode key, ImmutableSortedMap<String, HashCode> entries);
    void putResult(HashCode key, ImmutableSortedMap<String, HashCode> outputs, byte[] originMetadata);

    interface ContentProcessor {
        void processFile(InputStream inputStream) throws IOException;
        void processManifest(ImmutableSortedMap<String, HashCode> entries) throws IOException;
    }

    interface Result {
        ImmutableSortedMap<String, HashCode> getOutputs();
        InputStream getOriginMetadata();
    }
}
