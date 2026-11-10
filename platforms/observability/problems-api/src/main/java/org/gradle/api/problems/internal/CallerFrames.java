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

package org.gradle.api.problems.internal;

import org.gradle.internal.problems.failure.InternalStackTraceClassifier;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Finds the caller of an API in a stack: the first frame outside Gradle and outside the package of the API and its subpackages.
 * <p>
 * A package rather than a class, because a plugin's API spans many classes and Gradle's generated subclasses share their package;
 * not the jar or the class loader, because plugins ship as several jars and convention plugins share a class loader with the plugins they apply.
 */
public final class CallerFrames {

    private CallerFrames() {
    }

    @Nullable
    public static StackTraceElement firstOutside(List<StackTraceElement> stack, Class<?> api) {
        String apiPackage = packagePrefixOf(api);
        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            if (InternalStackTraceClassifier.isInternal(className) || className.startsWith(apiPackage)) {
                continue;
            }
            if (frame.getFileName() != null && frame.getLineNumber() > 0) {
                return frame;
            }
        }
        return null;
    }

    // Derived from the name, since Class.getPackageName() is Java 9; a nested class keeps its outer class's package
    private static String packagePrefixOf(Class<?> api) {
        String name = api.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot < 0 ? "" : name.substring(0, lastDot + 1);
    }
}
