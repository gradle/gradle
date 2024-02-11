/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.execution.history;

import com.google.common.collect.ImmutableListMultimap;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.hash.HashCode;

public class ImmutableWorkspaceMetadata {
    private final OriginMetadata originMetadata;
    private final ImmutableListMultimap<String, HashCode> outputPropertyHashes;

    public ImmutableWorkspaceMetadata(OriginMetadata originMetadata, ImmutableListMultimap<String, HashCode> outputPropertyHashes) {
        this.originMetadata = originMetadata;
        this.outputPropertyHashes = outputPropertyHashes;
    }

    public OriginMetadata getOriginMetadata() {
        return originMetadata;
    }

    public ImmutableListMultimap<String, HashCode> getOutputPropertyHashes() {
        return outputPropertyHashes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ImmutableWorkspaceMetadata metadata = (ImmutableWorkspaceMetadata) o;

        if (!originMetadata.equals(metadata.originMetadata)) {
            return false;
        }
        return outputPropertyHashes.equals(metadata.outputPropertyHashes);
    }

    @Override
    public int hashCode() {
        int result = originMetadata.hashCode();
        result = 31 * result + outputPropertyHashes.hashCode();
        return result;
    }
}
