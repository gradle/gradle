/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.internal.nativeintegration;

import org.gradle.internal.os.OperatingSystem;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Uses reflection to update private environment state
 */
public class ReflectiveEnvironment {

    public void unsetenv(String name) {
        Map<String, String> map = getEnv();
        map.remove(name);
        if (OperatingSystem.current().isWindows()) {
            Map<String, String> env2 = getWindowsEnv();
            env2.remove(name);
        }
    }

    public void setenv(String name, String value) {
        Map<String, String> map = getEnv();
        map.put(name, value);
        if (OperatingSystem.current().isWindows()) {
            Map<String, String> env2 = getWindowsEnv();
            env2.put(name, value);
        }
    }

    /**
     * Windows keeps an extra map with case insensitive keys. The map is used when the user calls {@link System#getenv(String)}
     */
    private Map<String, String> getWindowsEnv() {
        try {
            Class<?> sc = Class.forName("java.lang.ProcessEnvironment");
            Field caseinsensitive = sc.getDeclaredField("theCaseInsensitiveEnvironment");
            caseinsensitive.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, String> result = (Map<String, String>)caseinsensitive.get(null);
            return result;
        } catch (Exception e) {
            throw new NativeIntegrationException("Unable to get mutable windows case insensitive environment map", e);
        }
    }

    private Map<String, String> getEnv() {
        try {
            Map<String, String> theUnmodifiableEnvironment = System.getenv();
            Class<?> cu = theUnmodifiableEnvironment.getClass();
            Field m = cu.getDeclaredField("m");
            m.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, String> result = (Map<String, String>)m.get(theUnmodifiableEnvironment);
            return result;
        } catch (Exception e) {
            throw new NativeIntegrationException("Unable to get mutable environment map", e);
        }
    }
}
