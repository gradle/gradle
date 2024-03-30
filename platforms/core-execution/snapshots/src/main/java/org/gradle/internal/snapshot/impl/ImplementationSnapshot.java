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
import org.gradle.internal.snapshot.impl.UnknownImplementationSnapshot.UnknownReason;

import javax.annotation.Nullable;

import static org.gradle.internal.snapshot.impl.SerializedLambdaQueries.isLambdaClass;
import static org.gradle.internal.snapshot.impl.SerializedLambdaQueries.isLambdaClassName;
import static org.gradle.internal.snapshot.impl.SerializedLambdaQueries.serializedLambdaFor;

/**
 * Identifies a type in a classloader hierarchy. The type is identified by its name,
 * the classloader hierarchy by its hash code.
 */
public abstract class ImplementationSnapshot implements ValueSnapshot {

    protected final String classIdentifier;

    public static ImplementationSnapshot of(Class<?> type, ClassLoaderHierarchyHasher classLoaderHasher) {
        String className = type.getName();
        HashCode classLoaderHash = classLoaderHasher.getClassLoaderHash(type.getClassLoader());
        return of(className, classLoaderHash, isLambdaClass(type), null);
    }

    public static ImplementationSnapshot of(String className, @Nullable HashCode classLoaderHash) {
        return of(className, classLoaderHash, isLambdaClassName(className), null);
    }

    public static ImplementationSnapshot of(String className, Object value, @Nullable HashCode classLoaderHash) {
        return of(className, classLoaderHash, isLambdaClass(value.getClass()), value);
    }

    private static ImplementationSnapshot of(
        String classIdentifier,
        @Nullable HashCode classLoaderHash,
        boolean isLambda,
        @Nullable Object value
    ) {
        if (classLoaderHash == null) {
            return new UnknownImplementationSnapshot(classIdentifier, UnknownReason.UNKNOWN_CLASSLOADER);
        }

        if (isLambda) {
            return serializedLambdaFor(value)
                .<ImplementationSnapshot>map(it -> new LambdaImplementationSnapshot(classLoaderHash, it))
                .orElseGet(() -> new UnknownImplementationSnapshot(classIdentifier, UnknownReason.UNTRACKED_LAMBDA));
        }

        return new ClassImplementationSnapshot(classIdentifier, classLoaderHash);
    }

    protected ImplementationSnapshot(String classIdentifier) {
        this.classIdentifier = classIdentifier;
    }

    public String getClassIdentifier() {
        return classIdentifier;
    }

    @Nullable
    public abstract HashCode getClassLoaderHash();

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
