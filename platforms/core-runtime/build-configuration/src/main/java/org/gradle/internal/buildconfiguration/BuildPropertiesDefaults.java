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

package org.gradle.internal.buildconfiguration;

import org.gradle.api.JavaVersion;

public class BuildPropertiesDefaults {
    public static final String BUILD_PROPERTIES_FILE = "gradle/gradle-build.properties";

    public static final String TOOLCHAIN_VERSION_PROPERTY = "daemon.jvm.toolchain.version";
    public static final String TOOLCHAIN_VENDOR_PROPERTY = "daemon.jvm.toolchain.vendor";
    public static final String TOOLCHAIN_IMPLEMENTATION_PROPERTY = "daemon.jvm.toolchain.implementation";

    public static final JavaVersion TOOLCHAIN_VERSION = JavaVersion.current();
}
