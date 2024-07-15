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

package org.gradle.internal.instrumentation.reporting.listener;

import java.io.File;

public class OnInterceptedMethodInsFormatter {

    @SuppressWarnings("unused")
    public String format(File source, String className, String methodCallOwner, String methodName, String methodDescriptor, int lineNumber) {
        String methodCallOwnerClassName = methodCallOwner.replace("/", ".");
        return String.format("%s.%s(): at %s(%s.java:%d)", methodCallOwnerClassName, methodName, className, getClassName(className), lineNumber);
    }

    private static String getClassName(String relativePath) {
        String[] relativePathSplit = relativePath.split("[.]");
        return relativePathSplit[relativePathSplit.length - 1];
    }
}
