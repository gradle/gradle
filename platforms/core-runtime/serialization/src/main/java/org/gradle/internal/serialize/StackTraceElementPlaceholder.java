/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.serialize;

import org.gradle.api.JavaVersion;

import java.io.Serializable;

public class StackTraceElementPlaceholder implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String classLoaderName;
    private final String moduleName;
    private final String moduleVersion;
    private final String declaringClass;
    private final String methodName;
    private final String fileName;
    private final int lineNumber;

    @SuppressWarnings("Since15")
    public StackTraceElementPlaceholder(StackTraceElement ste) {
        if (JavaVersion.current().isJava9Compatible()) {
            classLoaderName = ste.getClassLoaderName();
            moduleName = ste.getModuleName();
            moduleVersion = ste.getModuleVersion();
        } else {
            classLoaderName = null;
            moduleName = null;
            moduleVersion = null;
        }
        declaringClass = ste.getClassName();
        methodName = ste.getMethodName();
        fileName = ste.getFileName();
        lineNumber = ste.getLineNumber();
    }

    public StackTraceElement toStackTraceElement() {
        if (JavaVersion.current().isJava9Compatible()) {
            return new StackTraceElement(classLoaderName, moduleName, moduleVersion, declaringClass, methodName, fileName, lineNumber);
        } else {
            return new StackTraceElement(declaringClass, methodName, fileName, lineNumber);
        }
    }
}
