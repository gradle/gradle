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
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.internal.JvmOptions;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Properties;

public class CurrentProcess {
    private final JavaInfo jvm;
    private final JvmOptions effectiveJvmOptions;

    public CurrentProcess() {
        this(Jvm.current(), inferJvmOptions());
    }

    CurrentProcess(JavaInfo jvm, JvmOptions effectiveJvmOptions) {
        this.jvm = jvm;
        this.effectiveJvmOptions = effectiveJvmOptions;
    }

    public JvmOptions getJvmOptions() {
        return effectiveJvmOptions;
    }

    /**
     * Attempts to configure the current process to run with the required build parameters.
     * @return True if the current process could be configured, false otherwise.
     */
    public boolean configureForBuild(DaemonParameters requiredBuildParameters) {
        boolean javaHomeMatch = jvm.equals(requiredBuildParameters.getEffectiveJvm());

        List<String> currentImmutable = new JvmOptions(new IdentityFileResolver()).getAllImmutableJvmArgs();
        List<String> requiredImmutable = requiredBuildParameters.getEffectiveJvmArgs();
        requiredImmutable.removeAll(DaemonParameters.DEFAULT_JVM_ARGS);
        boolean noImmutableJvmArgsRequired = requiredImmutable.equals(currentImmutable);

        if (javaHomeMatch && noImmutableJvmArgsRequired) {
            // Set the system properties and use this process
            Properties properties = new Properties();
            properties.putAll(requiredBuildParameters.getEffectiveSystemProperties());
            System.setProperties(properties);
            return true;
        }
        return false;
    }

    private static JvmOptions inferJvmOptions() {
        // Try to infer the effective jvm options for the currently running process.
        // We only care about 'managed' jvm args, anything else is unimportant to the running build
        JvmOptions jvmOptions = new JvmOptions(new IdentityFileResolver());
        jvmOptions.setAllJvmArgs(ManagementFactory.getRuntimeMXBean().getInputArguments());
        return jvmOptions;
    }
}
