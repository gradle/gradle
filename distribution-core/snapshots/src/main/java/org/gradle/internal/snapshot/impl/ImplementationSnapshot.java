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

package org.gradle.internal.snapshot.impl;

import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

import javax.annotation.Nullable;

/**
 * Identifies a type in a classloader hierarchy. The type is identified by its name,
 * the classloader hierarchy by its hash code.
 */
public abstract class ImplementationSnapshot implements ValueSnapshot {
    private static final String GENERATED_LAMBDA_CLASS_SUFFIX = "$$Lambda$";

    private final String typeName;

    public static ImplementationSnapshot of(Class<?> type, ClassLoaderHierarchyHasher classLoaderHasher) {
        String className = type.getName();
        return of(className, classLoaderHasher.getClassLoaderHash(type.getClassLoader()), type.isSynthetic() && isLambdaClassName(className));
    }

    public static ImplementationSnapshot of(String className, @Nullable HashCode classLoaderHash) {
        return of(className, classLoaderHash, isLambdaClassName(className));
    }

    private static ImplementationSnapshot of(String typeName, @Nullable HashCode classLoaderHash, boolean lambda) {
        if (classLoaderHash == null) {
            return new UnknownClassloaderImplementationSnapshot(typeName);
        }
        if (lambda) {
            return new LambdaImplementationSnapshot(typeName);
        }
        return new DefaultImplementationSnapshot(typeName, classLoaderHash);
    }

    private static boolean isLambdaClassName(String className) {
        return className.contains(GENERATED_LAMBDA_CLASS_SUFFIX);
    }

    protected ImplementationSnapshot(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }

    @Nullable
    public abstract HashCode getClassLoaderHash();

    public abstract boolean isUnknown();

    @Nullable
    public abstract String getUnknownReason();

    @Override
    public ValueSnapshot snapshot(@Nullable Object value, ValueSnapshotter snapshotter) {
        ValueSnapshot other = snapshotter.snapshot(value);
        if (this.isSameSnapshot(other)) {
            return this;
        }
        return other;
    }

    protected abstract boolean isSameSnapshot(@Nullable Object o);
}
