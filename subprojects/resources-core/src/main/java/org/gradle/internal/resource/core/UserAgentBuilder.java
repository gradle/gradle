/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.resource.core;

import org.gradle.internal.SystemProperties;
import org.gradle.util.GradleVersion;

public class UserAgentBuilder {
    public static String getUserAgentString() {
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        String javaVendor = System.getProperty("java.vendor");
        String javaVersion = SystemProperties.getInstance().getJavaVersion();
        String javaVendorVersion = System.getProperty("java.vm.version");
        return String.format("Gradle/%s (%s;%s;%s) (%s;%s;%s)",
            GradleVersion.current().getVersion(),
            osName,
            osVersion,
            osArch,
            javaVendor,
            javaVersion,
            javaVendorVersion);
    }
}
