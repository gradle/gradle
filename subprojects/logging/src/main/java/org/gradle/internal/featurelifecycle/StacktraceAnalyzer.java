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

package org.gradle.internal.featurelifecycle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.internal.classloader.ClasspathUtil.getClasspathForClass;
import static org.gradle.internal.featurelifecycle.FeatureInvocationSource.SourceType;

class StacktraceAnalyzer {
    static List<StackTraceElement> getCleansedStackTrace(Class<?> calledFrom) {
        StackTraceElement[] originalStack = new Exception().getStackTrace();
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

        caller = skipSystemStackElements(originalStack, caller);

        List<StackTraceElement> result = new ArrayList<StackTraceElement>();
        for (; caller < originalStack.length; caller++) {
            result.add(originalStack[caller]);
        }
        return result;
    }

    private static int skipSystemStackElements(StackTraceElement[] stackTrace, int caller) {
        for (; caller < stackTrace.length; caller++) {
            String currentClassName = stackTrace[caller].getClassName();
            if (!isSystemInternal(currentClassName)) {
                break;
            }
        }
        return caller;
    }

    static FeatureInvocationSource analyzeInvocation(FeatureUsage usage) {
        for (StackTraceElement element : usage.getStack()) {
            SourceType sourceType = getMethodSourceType(element);
            if (isDecisive(sourceType)) {
                return new FeatureInvocationSource(usage.getMessage(), sourceType);
            }
        }

        return new FeatureInvocationSource(usage.getMessage(), SourceType.UNKNOWN);
    }

    private static boolean isDecisive(SourceType sourceType) {
        return sourceType == SourceType.BUILD_SRC || sourceType == SourceType.THIRD_PARTY_JAR || sourceType == SourceType.SCRIPT;
    }

    private static SourceType getMethodSourceType(StackTraceElement element) {
        if (element.getFileName() == null) {
            return SourceType.UNKNOWN;
        }
        if (isGradleScript(element)) {
            return SourceType.SCRIPT;
        }
        if (isGradleInternal(element.getClassName())) {
            return SourceType.GRADLE_INTERNAL;
        }
        try {
            File file = getClasspathForClass(element.getClassName());
            if (file == null) {
                return SourceType.UNKNOWN;
            }
            if (isBuildSrc(file)) {
                return SourceType.BUILD_SRC;
            }
            if (isThirdPartyJar(file)) {
                return SourceType.THIRD_PARTY_JAR;
            }
            return SourceType.UNKNOWN;
        } catch (Throwable e) {
            return SourceType.UNKNOWN;
        }
    }

    private static boolean isBuildSrc(File file) {
        return "buildSrc.jar".equals(file.getName());
    }

    private static boolean isSystemInternal(String className) {
        return className.startsWith("org.codehaus.groovy.")
            || className.startsWith("org.gradle.internal.metaobject.")
            || className.startsWith("groovy.")
            || className.startsWith("java.")
            || className.startsWith("sun.")
            || className.startsWith("jdk.internal.");
    }

    private static boolean isGradleInternal(String className) {
        return className.startsWith("org.gradle") || isSystemInternal(className);
    }

    private static boolean isThirdPartyJar(File file) {
        return file != null && file.getName().toLowerCase().endsWith(".jar");
    }

    private static boolean isGradleScript(StackTraceElement stackTraceElement) {
        String className = stackTraceElement.getClassName();
        String fileName = stackTraceElement.getFileName();
        return className.startsWith("build_") || className.startsWith("http_") || fileName.endsWith(".gradle");
    }
}
