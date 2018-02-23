/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.jvm;

import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GradleVersion;

public class UnsupportedJavaRuntimeException extends GradleException {
    private static final DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry();
    public static final String JAVA7_DEPRECATION_WARNING = "Support for running Gradle using Java 7 has been deprecated and is scheduled to be removed in Gradle 5.0. ";
    private static final String JAVA7_DEPRECATION_WARNING_DOC =
        JAVA7_DEPRECATION_WARNING + "Please see " + DOCUMENTATION_REGISTRY.getDocumentationFor("java_plugin", "sec:java_cross_compilation") + " for more details.";

    public UnsupportedJavaRuntimeException(String message) {
        super(message);
    }

    public static void javaDeprecationWarning() {
        if (!JavaVersion.current().isJava8Compatible()) {
            DeprecationLogger.nagUserWith(JAVA7_DEPRECATION_WARNING_DOC);
        }
    }

    public static void assertUsingVersion(String component, JavaVersion minVersion) throws UnsupportedJavaRuntimeException {
        JavaVersion current = JavaVersion.current();
        if (current.compareTo(minVersion) >= 0) {
            return;
        }
        throw new UnsupportedJavaRuntimeException(String.format("%s %s requires Java %s or later to run. You are currently using Java %s.", component, GradleVersion.current().getVersion(),
            minVersion.getMajorVersion(), current.getMajorVersion()));
    }

    public static void assertUsingVersion(String component, JavaVersion minVersion, JavaVersion configuredVersion) throws UnsupportedJavaRuntimeException {
        if (configuredVersion.compareTo(minVersion) >= 0) {
            return;
        }
        throw new UnsupportedJavaRuntimeException(String.format("%s %s requires Java %s or later to run. Your build is currently configured to use Java %s.", component, GradleVersion.current().getVersion(),
            minVersion.getMajorVersion(), configuredVersion.getMajorVersion()));
    }
}
