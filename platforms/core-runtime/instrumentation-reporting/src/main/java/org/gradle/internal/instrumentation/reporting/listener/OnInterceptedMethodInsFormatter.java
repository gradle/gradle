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

public class OnInterceptedMethodInsFormatter {

    @SuppressWarnings("unused")
    public String format(String sourceFileName, String className, String methodCallOwner, String methodName, String methodDescriptor, int lineNumber) {
        String methodCallOwnerClassName = methodCallOwner.replace("/", ".");
        // Gradle Kotlin scripts have a weird class name so IntelliJ stacktrace parser doesn't parse them well
        className = sourceFileName.endsWith("gradle.kts")
            ? sourceFileName.replace(".kts", "")
            : className.replace("/", ".");
        return String.format("%s.%s(): at %s(%s:%d)", methodCallOwnerClassName, methodName, className, sourceFileName, lineNumber);
    }
}
