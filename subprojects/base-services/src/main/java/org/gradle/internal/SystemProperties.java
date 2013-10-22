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

import java.io.File;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.ImmutableSet.of;

/**
 * Provides access to frequently used system properties.
 */
public class SystemProperties {
    private static final Set<String> STANDARD_PROPERTIES = of(
            "java.version",
            "java.vendor",
            "java.vendor.url",
            "java.home",
            "java.vm.specification.version",
            "java.vm.specification.vendor",
            "java.vm.specification.name",
            "java.vm.version",
            "java.vm.vendor",
            "java.vm.name",
            "java.specification.version",
            "java.specification.vendor",
            "java.specification.name",
            "java.class.version",
            "java.class.path",
            "java.library.path",
            "java.io.tmpdir",
            "java.compiler",
            "java.ext.dirs",
            "os.name",
            "os.arch",
            "os.version",
            "file.separator",
            "path.separator",
            "line.separator",
            "user.name",
            "user.home",
            "user.dir"
    );

    private static final Set<String> IMPORTANT_NON_STANDARD_PROPERTIES = of(
            "java.runtime.version"
    );

    @SuppressWarnings("unchecked")
    public static Map<String, String> asMap() {
        return (Map) System.getProperties();
    }

    public static String getLineSeparator() {
        return System.getProperty("line.separator");
    }

    public static String getJavaIoTmpDir() {
        return System.getProperty("java.io.tmpdir");
    }

    public static String getUserHome() {
        return System.getProperty("user.home");
    }

    public static String getJavaVersion() {
        return System.getProperty("java.version");
    }

    public static File getCurrentDir() {
        return new File(System.getProperty("user.dir"));
    }

    /**
     * Returns the keys that are guaranteed to be contained in System.getProperties() by default,
     * as specified in the Javadoc for that method.
     */
    public static Set<String> getStandardProperties() {
        return STANDARD_PROPERTIES;
    }

    /**
     * Returns the names of properties that are not guaranteed to be contained in System.getProperties()
     * but are usually there and if there should not be adjusted.
     *
     * @return the set of keys of {@code System.getProperties()} which should not be adjusted
     *   by client code. This method never returns {@code null}.
     */
    public static Set<String> getNonStandardImportantProperties() {
        return IMPORTANT_NON_STANDARD_PROPERTIES;
    }
}
