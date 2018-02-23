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

import org.gradle.internal.hash.HashCode;

import java.io.File;

public class IncludeFileState {
    private final HashCode hash;
    private final File includeFile;

    public IncludeFileState(HashCode hash, File includeFile) {
        this.hash = hash;
        this.includeFile = includeFile;
    }

    public HashCode getHash() {
        return hash;
    }

    public File getIncludeFile() {
        return includeFile;
    }

    @Override
    public String toString() {
        return includeFile.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        IncludeFileState other = (IncludeFileState) obj;
        return hash.equals(other.hash) && includeFile.equals(other.includeFile);
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }
}
