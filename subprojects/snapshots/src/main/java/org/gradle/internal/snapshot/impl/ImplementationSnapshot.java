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
    public enum UnknownReason {
        LAMBDA(
            "was implemented by the Java lambda '%s'.",
            "Using Java lambdas is not supported as task inputs.",
            "Use an (anonymous inner) class instead."),
        UNKNOWN_CLASSLOADER(
            "was loaded with an unknown classloader (class '%s').",
            "Gradle cannot track the implementation for classes loaded with an unknown classloader.",
            "Load your class by using one of Gradle's built-in ways."
        );

        private final String descriptionTemplate;
        private final String reason;
        private final String solution;

        UnknownReason(String descriptionTemplate, String reason, String solution) {
            this.descriptionTemplate = descriptionTemplate;
            this.reason = reason;
            this.solution = solution;
        }

        public String descriptionFor(ImplementationSnapshot implementationSnapshot) {
            return String.format(descriptionTemplate, implementationSnapshot.getTypeName());
        }

        public String getReason() {
            return reason;
        }

        public String getSolution() {
            return solution;
        }
    }

    private final String typeName;

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
        String typeName,
        @Nullable HashCode classLoaderHash,
        boolean isLambda,
        @Nullable Object value
    ) {
        if (classLoaderHash == null) {
            return new UnknownClassloaderImplementationSnapshot(typeName);
        }

        if (isLambda) {
            return Optional.ofNullable(value)
                .flatMap(ImplementationSnapshot::serializedLambdaFor)
                .<ImplementationSnapshot>map(it -> new SerializableLambdaImplementationSnapshot(classLoaderHash, it))
                .orElseGet(() -> new LambdaImplementationSnapshot(typeName));
        }

        return new KnownImplementationSnapshot(typeName, classLoaderHash);
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
    public abstract UnknownReason getUnknownReason();

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
