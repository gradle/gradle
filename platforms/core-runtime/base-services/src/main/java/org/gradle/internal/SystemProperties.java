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
package org.gradle.internal;

import com.google.common.collect.Maps;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * Provides access to frequently used system properties.
 */
public class SystemProperties {
    private static final Set<String> STANDARD_PROPERTIES;

    static {
        Set<String> standardProperties = new HashSet<String>();
        standardProperties.add("java.version");
        standardProperties.add("java.vendor");
        standardProperties.add("java.vendor.url");
        standardProperties.add("java.home");
        standardProperties.add("java.vm.specification.version");
        standardProperties.add("java.vm.specification.vendor");
        standardProperties.add("java.vm.specification.name");
        standardProperties.add("java.vm.version");
        standardProperties.add("java.vm.vendor");
        standardProperties.add("java.vm.name");
        standardProperties.add("java.specification.version");
        standardProperties.add("java.specification.vendor");
        standardProperties.add("java.specification.name");
        standardProperties.add("java.class.version");
        standardProperties.add("java.class.path");
        standardProperties.add("java.library.path");
        standardProperties.add("java.io.tmpdir");
        standardProperties.add("java.compiler");
        standardProperties.add("java.ext.dirs");
        standardProperties.add("os.name");
        standardProperties.add("os.arch");
        standardProperties.add("os.version");
        standardProperties.add("file.separator");
        standardProperties.add("path.separator");
        standardProperties.add("line.separator");
        standardProperties.add("user.name");
        standardProperties.add("user.home");
        standardProperties.add("user.dir");
        STANDARD_PROPERTIES = Collections.unmodifiableSet(standardProperties);
    }

    private static final Set<String> IMPORTANT_NON_STANDARD_PROPERTIES = Collections.singleton("java.runtime.version");

    private static final SystemProperties INSTANCE = new SystemProperties();

    public static SystemProperties getInstance() {
        return INSTANCE;
    }

    private SystemProperties() {
    }

    public String getLineSeparator() {
        return System.getProperty("line.separator");
    }

    /**
     * @deprecated Using the temporary directory on UNIX-based systems can lead to local privilege escalation or local sensitive information disclosure vulnerabilities.
     */
    @Deprecated
    public String getJavaIoTmpDir() {
        return System.getProperty("java.io.tmpdir");
    }

    public String getUserHome() {
        return System.getProperty("user.home");
    }

    public String getUserName() {
        return System.getProperty("user.name");
    }

    public String getJavaVersion() {
        return System.getProperty("java.version");
    }

    public File getCurrentDir() {
        return new File(System.getProperty("user.dir"));
    }

    @Nullable
    public String getWorkerTmpDir() {
        return System.getProperty("org.gradle.internal.worker.tmpdir");
    }

    /**
     * Creates an instance for a Factory implementation with a system property set to a given value.  Sets the system property back to the original value (or
     * clears it if it was never set) after the operation.
     *
     * @param propertyName The name of the property to set
     * @param value The value to temporarily set the property to
     * @param factory Instance created by the Factory implementation
     */
    public synchronized <T> T withSystemProperty(String propertyName, String value, Factory<T> factory) {
        String originalValue = overrideProperty(propertyName, value);

        try {
            return factory.create();
        } finally {
            restoreProperty(propertyName, originalValue);
        }
    }

    /**
     * Provides safe access to the system properties, preventing concurrent {@link #withSystemProperty(String, String, Factory)} calls.
     *
     * This can be used to wrap 3rd party APIs that iterate over the system properties, so they won't result in {@link java.util.ConcurrentModificationException}s.
     *
     * This method should not be used when you need to temporarily change system properties.
     */
    public synchronized <T> T withSystemProperties(Factory<T> factory) {
        return factory.create();
    }

    /**
     * Provides safe access to the system properties, preventing concurrent calls to change system properties.
     *
     * This can be used to wrap 3rd party APIs that iterate over the system properties, so they won't result in {@link java.util.ConcurrentModificationException}s.
     *
     * This method can be used to override system properties temporarily.  The original values of the given system properties are restored before returning.
     */
    public synchronized <T> T withSystemProperties(Map<String, String> properties, Factory<T> factory) {
        Map<String, String> originalProperties = Maps.newHashMap();
        for (Map.Entry<String, String> property : properties.entrySet()) {
            String propertyName = property.getKey();
            String value = property.getValue();
            String originalValue = overrideProperty(propertyName, value);
            originalProperties.put(propertyName, originalValue);
        }

        try {
            return factory.create();
        } finally {
            for (Map.Entry<String, String> property : originalProperties.entrySet()) {
                String propertyName = property.getKey();
                String originalValue = property.getValue();
                restoreProperty(propertyName, originalValue);
            }
        }
    }

    @Nullable
    private String overrideProperty(String propertyName, String value) {
        // Overwrite property
        String originalValue = System.getProperty(propertyName);
        if (value != null) {
            System.setProperty(propertyName, value);
        } else {
            System.clearProperty(propertyName);
        }
        return originalValue;
    }

    private void restoreProperty(String propertyName, @Nullable String originalValue) {
        if (originalValue != null) {
            System.setProperty(propertyName, originalValue);
        } else {
            System.clearProperty(propertyName);
        }
    }

    /**
     * Returns true if the given key is guaranteed to be contained in System.getProperties() by default,
     * as specified in the Javadoc for that method.
     */
    public boolean isStandardProperty(String key) {
        return STANDARD_PROPERTIES.contains(key);
    }

    /**
     * Returns true if the key is an important property that is not guaranteed to be contained in System.getProperties().
     * The property is usually there and should not be adjusted.
     */
    public boolean isNonStandardImportantProperty(String key) {
        return IMPORTANT_NON_STANDARD_PROPERTIES.contains(key);
    }
}
