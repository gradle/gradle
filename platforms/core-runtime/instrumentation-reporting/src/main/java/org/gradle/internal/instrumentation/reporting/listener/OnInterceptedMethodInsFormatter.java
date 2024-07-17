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

import javax.annotation.Nullable;
import java.io.File;

public class OnInterceptedMethodInsFormatter {

    @SuppressWarnings("unused")
    public String format(@Nullable File source, String sourceFileName, String className, String methodCallOwner, String methodName, String methodDescriptor, int lineNumber) {
        String methodCallOwnerClassName = methodCallOwner.replace("/", ".");
        // Gradle Kotlin scripts have a weird class name so IntelliJ stacktrace parser doesn't parse them well
        className = sourceFileName.endsWith("gradle.kts")
            ? sourceFileName.replace(".kts", "")
            : className.replace("/", ".");
        // Build scripts are all named the same, so IntelliJ jump-to-source functionality is not that useful, so let's use the path.
        // Note: For other dependencies a source could be a jar, so using absolute path directly might not be useful.
        sourceFileName = source != null && isAnyBuildScript(sourceFileName)
            ? "file://" + source.getAbsolutePath()
            : sourceFileName;
        return String.format("%s.%s(): at %s(%s:%d)", methodCallOwnerClassName, methodName, className, sourceFileName, lineNumber);
    }

    private static boolean isAnyBuildScript(String sourceFileName) {
        switch (sourceFileName) {
            case "init.gradle.kts":
            case "settings.gradle.kts":
            case "build.gradle.kts":
            case "init.gradle":
            case "settings.gradle":
            case "build.gradle":
                return true;
            default:
                return false;
        }
    }
}
