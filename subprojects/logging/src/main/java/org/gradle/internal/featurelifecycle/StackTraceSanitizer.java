/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.featurelifecycle;

import org.gradle.problems.buildtree.ProblemStream;

import java.util.ArrayList;
import java.util.List;

class StackTraceSanitizer implements ProblemStream.StackTraceTransformer {
    private final Class<?> calledFrom;

    public StackTraceSanitizer(Class<?> calledFrom) {
        this.calledFrom = calledFrom;
    }

    @Override
    public List<StackTraceElement> transform(StackTraceElement[] originalStack) {
        List<StackTraceElement> result = new ArrayList<StackTraceElement>();
        final String calledFromName = calledFrom.getName();
        boolean calledFromFound = false;
        int caller;
        for (caller = 0; caller < originalStack.length; caller++) {
            StackTraceElement current = originalStack[caller];
            if (!calledFromFound) {
                if (current.getClassName().startsWith(calledFromName)) {
                    calledFromFound = true;
                }
            } else {
                if (!current.getClassName().startsWith(calledFromName)) {
                    break;
                }
            }
        }
        for (; caller < originalStack.length; caller++) {
            StackTraceElement stackTraceElement = originalStack[caller];
            if (!isSystemStackFrame(stackTraceElement.getClassName())) {
                result.add(stackTraceElement);
            }
        }
        return result;
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
