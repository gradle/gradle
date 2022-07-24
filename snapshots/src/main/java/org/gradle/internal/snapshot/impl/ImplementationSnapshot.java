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
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Identifies a type in a classloader hierarchy. The type is identified by its name,
 * the classloader hierarchy by its hash code.
 */
public abstract class ImplementationSnapshot implements ValueSnapshot {

    private static final String GENERATED_LAMBDA_CLASS_SUFFIX = "$$Lambda$";

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

    private static boolean isLambdaClass(Class<?> type) {
        return type.isSynthetic() && isLambdaClassName(type.getName());
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
            return Optional.ofNullable(value)
                .flatMap(ImplementationSnapshot::serializedLambdaFor)
                .<ImplementationSnapshot>map(it -> new LambdaImplementationSnapshot(classLoaderHash, it))
                .orElseGet(() -> new UnknownImplementationSnapshot(classIdentifier, UnknownReason.UNTRACKED_LAMBDA));
        }

        return new ClassImplementationSnapshot(classIdentifier, classLoaderHash);
    }

    private static Optional<SerializedLambda> serializedLambdaFor(Object lambda) {
        if (!(lambda instanceof Serializable)) {
            return Optional.empty();
        }
        for (Class<?> lambdaClass = lambda.getClass(); lambdaClass != null; lambdaClass = lambdaClass.getSuperclass()) {
            try {
                Method replaceMethod = lambdaClass.getDeclaredMethod("writeReplace");
                replaceMethod.setAccessible(true);
                Object serializedForm = replaceMethod.invoke(lambda);
                if (serializedForm instanceof SerializedLambda) {
                    return Optional.of((SerializedLambda) serializedForm);
                } else {
                    return Optional.empty();
                }
            } catch (NoSuchMethodException e) {
                // continue
            } catch (InvocationTargetException | IllegalAccessException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean isLambdaClassName(String className) {
        return className.contains(GENERATED_LAMBDA_CLASS_SUFFIX);
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
