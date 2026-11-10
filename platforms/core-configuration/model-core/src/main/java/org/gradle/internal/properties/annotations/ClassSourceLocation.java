/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.properties.annotations;

import com.google.common.collect.ImmutableMap;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

/**
 * Source location information extracted from a class file.
 */
public class ClassSourceLocation {
    public static final ClassSourceLocation UNKNOWN = new ClassSourceLocation(null, ImmutableMap.of());

    @Nullable
    private final String sourceFilename;
    private final ImmutableMap<String, Integer> lineNumbersByMethod;

    public ClassSourceLocation(@Nullable String sourceFilename, Map<String, Integer> lineNumbersByMethod) {
        this.sourceFilename = sourceFilename;
        this.lineNumbersByMethod = ImmutableMap.copyOf(lineNumbersByMethod);
    }

    /**
     * Returns the source filename, or {@code null} if unknown.
     */
    @Nullable
    public String getSourceFilename() {
        return sourceFilename;
    }

    /**
     * Returns the line number for the given method, or {@code null} if unknown.
     */
    @Nullable
    public Integer getLineNumber(Method method) {
        return getLineNumber(method.getName(), Type.getMethodDescriptor(method));
    }

    /**
     * Returns the line number for the method identified by name and JVM descriptor, or {@code null} if unknown.
     */
    @Nullable
    public Integer getLineNumber(String methodName, String methodDescriptor) {
        return lineNumbersByMethod.get(methodKey(methodName, methodDescriptor));
    }

    public static String methodKey(String methodName, String methodDescriptor) {
        return methodName + methodDescriptor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ClassSourceLocation that = (ClassSourceLocation) o;
        return Objects.equals(sourceFilename, that.sourceFilename) &&
            Objects.equals(lineNumbersByMethod, that.lineNumbersByMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceFilename, lineNumbersByMethod);
    }
}
