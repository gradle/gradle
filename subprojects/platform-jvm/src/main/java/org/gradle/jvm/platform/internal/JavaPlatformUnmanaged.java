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

package org.gradle.jvm.platform.internal;

import org.gradle.api.JavaVersion;
import org.gradle.jvm.platform.JavaPlatform;

public class JavaPlatformUnmanaged implements JavaPlatform {
    private final String name;
    private JavaVersion targetCompatibility;

    public JavaPlatformUnmanaged(String name) {
        this.name = name;
        this.targetCompatibility = JavaVersion.current();
    }

    public JavaPlatformUnmanaged(JavaVersion javaVersion) {
        this.name = generateName(javaVersion);
        this.targetCompatibility = javaVersion;
    }

    public JavaVersion getTargetCompatibility() {
        return targetCompatibility;
    }

    public String getDisplayName() {
        return generateDisplayName(targetCompatibility);
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return getDisplayName();
    }

    public void setTargetCompatibility(JavaVersion targetCompatibility) {
        this.targetCompatibility = targetCompatibility;
    }

    public static String generateDisplayName(JavaVersion javaVersion) {
        return String.format("Java SE %s", javaVersion.getMajorVersion());
    }

    public static String generateName(JavaVersion javaVersion) {
        return "java" + javaVersion.getMajorVersion();
    }
}
