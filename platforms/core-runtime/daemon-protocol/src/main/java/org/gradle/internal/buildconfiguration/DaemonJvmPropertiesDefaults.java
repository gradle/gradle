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

public class DaemonJvmPropertiesDefaults {
    public static final String DAEMON_JVM_PROPERTIES_FILE = "gradle/gradle-daemon-jvm.properties";

    public static final String TOOLCHAIN_VERSION_PROPERTY = "toolchainVersion";
    public static final String TOOLCHAIN_VENDOR_PROPERTY = "toolchainVendor";
    public static final String TOOLCHAIN_IMPLEMENTATION_PROPERTY = "toolchainImplementation";
    public static final String TOOLCHAIN_URL_PROPERTY_FORMAT = "toolchain%s%sUrl";
}
