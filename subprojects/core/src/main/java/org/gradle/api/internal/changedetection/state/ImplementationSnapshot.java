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
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;

/**
 * Identifies a type in a classloader hierarchy. The type is identified by its name,
 * the classloader hierarchy by its hash code.
 */
public class ImplementationSnapshot implements ValueSnapshot {
    private static final String GENERATED_LAMBDA_CLASS_SUFFIX = "$$Lambda$";

    private final String typeName;
    private final HashCode classLoaderHash;
    private final boolean lambda;

    public static ImplementationSnapshot of(Class<?> type, ClassLoaderHierarchyHasher classLoaderHasher) {
        String className = type.getName();
        return of(className, classLoaderHasher.getClassLoaderHash(type.getClassLoader()), type.isSynthetic() && isLambdaClassName(className));
    }

    public static ImplementationSnapshot of(String typeName, @Nullable HashCode classLoaderHash, boolean lambda) {
        return new ImplementationSnapshot(typeName, classLoaderHash, lambda);
    }

    public static ImplementationSnapshot of(String className, @Nullable HashCode classLoaderHash) {
        return of(className, classLoaderHash, isLambdaClassName(className));
    }

    private static boolean isLambdaClassName(String className) {
        return className.contains(GENERATED_LAMBDA_CLASS_SUFFIX);
    }

    private ImplementationSnapshot(String typeName, @Nullable HashCode classLoaderHash, boolean lambda) {
        this.typeName = typeName;
        this.classLoaderHash = classLoaderHash;
        this.lambda = lambda;
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
        if (isKnown()) {
            hasher.putString(ImplementationSnapshot.class.getName());
            hasher.putString(typeName);
            hasher.putHash(classLoaderHash);
        } else {
            hasher.markAsInvalid();
        }
    }

    public boolean isKnown() {
        return getUnknownImplementationMessage() == null;
    }

    @Nullable
    public String getUnknownImplementationMessage() {
        if (classLoaderHash == null) {
            return "was loaded with an unknown classloader";
        }
        if (lambda) {
            return "was implemented by a Java 8 lambda";
        }
        return null;
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshotter snapshotter) {
        ValueSnapshot other = snapshotter.snapshot(value);
        if (this.isSameSnapshot(other)) {
            return this;
        }
        return other;
    }

    private boolean isSameSnapshot(Object o) {
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
        return classLoaderHash != null ? classLoaderHash.equals(that.classLoaderHash) : that.classLoaderHash == null;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ImplementationSnapshot that = (ImplementationSnapshot) o;
        if (!isKnown() || !that.isKnown()) {
            return false;
        }
        if (this == o) {
            return true;
        }


        if (!typeName.equals(that.typeName)) {
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
