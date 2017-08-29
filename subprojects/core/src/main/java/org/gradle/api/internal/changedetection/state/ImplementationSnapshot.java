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

package org.gradle.api.internal.changedetection.state;

import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;

/**
 * Identifies a type in a classloader hierarchy. The type is identified by its name,
 * the classloader hierarchy by its hash code.
 */
public class ImplementationSnapshot implements Snapshot {
    private final String typeName;
    private final HashCode classLoaderHash;

    public ImplementationSnapshot(String typeName, @Nullable HashCode classLoaderHash) {
        this.typeName = typeName;
        this.classLoaderHash = classLoaderHash;
    }

    public String getTypeName() {
        return typeName;
    }

    public HashCode getClassLoaderHash() {
        if (classLoaderHash == null) {
            throw new NullPointerException("classLoaderHash");
        }
        return classLoaderHash;
    }

    public boolean hasUnknownClassLoader() {
        return classLoaderHash == null;
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putString(ImplementationSnapshot.class.getName());
        hasher.putString(typeName);
        hasher.putHash(classLoaderHash);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ImplementationSnapshot that = (ImplementationSnapshot) o;

        if (!typeName.equals(that.typeName)) {
            return false;
        }
        if (classLoaderHash == null || that.classLoaderHash == null) {
            return false;
        }
        return classLoaderHash.equals(that.classLoaderHash);
    }

    @Override
    public int hashCode() {
        int result = typeName.hashCode();
        result = 31 * result + (classLoaderHash != null ? classLoaderHash.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return typeName + "@" + classLoaderHash;
    }
}
