/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.metadata;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.hash.Hasher;

public class DefaultImmutableMetadataSources implements ImmutableMetadataSources {
    private final ImmutableList<MetadataSource<?>> sources;

    public DefaultImmutableMetadataSources(Iterable<MetadataSource<?>> sources) {
        this.sources = ImmutableList.copyOf(sources);
    }

    @Override
    public ImmutableList<MetadataSource<?>> sources() {
        return sources;
    }

    @Override
    public void appendId(Hasher hasher) {
        hasher.putInt(sources.size());
        for (MetadataSource<?> source : sources) {
            source.appendId(hasher);
        }
    }
}
