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

package org.gradle.language.nativeplatform.internal.incremental;

import com.google.common.base.Objects;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;

public class IncludeFileEdge {
    private final String includePath;
    @Nullable
    private final HashCode includedBy;
    private final HashCode resolvedTo;

    public IncludeFileEdge(String includePath, @Nullable HashCode includedBy, HashCode resolvedTo) {
        this.includePath = includePath;
        this.includedBy = includedBy;
        this.resolvedTo = resolvedTo;
    }

    public String getIncludePath() {
        return includePath;
    }

    @Nullable
    public HashCode getIncludedBy() {
        return includedBy;
    }

    public HashCode getResolvedTo() {
        return resolvedTo;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        IncludeFileEdge other = (IncludeFileEdge) obj;
        return includePath.equals(other.includePath) && Objects.equal(includedBy, other.includedBy) && resolvedTo.equals(other.resolvedTo);
    }

    @Override
    public int hashCode() {
        return includePath.hashCode();
    }
}
