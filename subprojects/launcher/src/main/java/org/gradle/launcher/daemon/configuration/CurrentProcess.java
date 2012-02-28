/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.daemon.configuration;

import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.process.internal.JvmOptions;
import org.gradle.util.Jvm;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Map;

public class CurrentProcess {
    private final File javaHome;
    private final JvmOptions effectiveJvmOptions;

    public CurrentProcess() {
        this(Jvm.current().getJavaHome(), inferJvmOptions());
    }

    public CurrentProcess(File javaHome, JvmOptions effectiveJvmOptions) {
        this.javaHome = javaHome;
        this.effectiveJvmOptions = effectiveJvmOptions;
    }

    public JvmOptions getJvmOptions() {
        return effectiveJvmOptions;
    }

    public File getJavaHome() {
        return javaHome;
    }

    public boolean supportsBuildParameters(DaemonParameters requiredBuildParameters) {
        return hasJavaHome(requiredBuildParameters)
                && hasJvmArgs(requiredBuildParameters)
                && hasSystemProperties(requiredBuildParameters);
    }

    private boolean hasJavaHome(DaemonParameters requiredJavaHome) {
        return getJavaHome().equals(requiredJavaHome.getEffectiveJavaHome());
    }

    private boolean hasJvmArgs(DaemonParameters requiredBuildParameters) {
        return requiredBuildParameters.isUsingDefaultJvmArgs() || (effectiveJvmOptions.getManagedJvmArgs().equals(requiredBuildParameters.getEffectiveJvmArgs()));
    }

    private boolean hasSystemProperties(DaemonParameters requiredBuildParameters) {
        return containsAll(getJvmOptions().getSystemProperties(), requiredBuildParameters.getSystemProperties());
    }

    private boolean containsAll(Map<String, ?> systemProperties, Map<String, ?> requiredSystemProperties) {
        for (String key : requiredSystemProperties.keySet()) {
            if (!requiredSystemProperties.get(key).equals(systemProperties.get(key))) {
                return false;
            }
        }
        return true;
    }

    private static JvmOptions inferJvmOptions() {
        // Try to infer the effective jvm options for the currently running process.
        // We only care about 'managed' jvm args, anything else is unimportant to the running build
        JvmOptions jvmOptions = new JvmOptions(new IdentityFileResolver());
        jvmOptions.setAllJvmArgs(ManagementFactory.getRuntimeMXBean().getInputArguments());
        return jvmOptions;
    }
}
