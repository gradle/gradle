/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.failure;

import org.gradle.internal.problems.failure.StackTraceRelevance;

import javax.annotation.Nullable;

public class SystemCallStackTraceClassifier implements StackTraceClassifier {

    @Nullable
    @Override
    public StackTraceRelevance classify(StackTraceElement frame) {
        return isSystemStackFrame(frame.getClassName()) ? StackTraceRelevance.SYSTEM : null;
    }

    private static boolean isSystemStackFrame(String className) {
        return className.startsWith("jdk.internal.") ||
            className.startsWith("sun.") ||
            className.startsWith("com.sun.") ||
            className.startsWith("org.codehaus.groovy.") ||
            className.startsWith("org.gradle.internal.metaobject.") ||
            className.startsWith("org.gradle.kotlin.dsl.execution.");
    }
}
