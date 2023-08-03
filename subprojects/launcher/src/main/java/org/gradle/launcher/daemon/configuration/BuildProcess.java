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

package org.gradle.launcher.daemon.configuration;

import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.agents.AgentStatus;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.process.internal.CurrentProcess;
import org.gradle.process.internal.JvmOptions;

import java.util.List;
import java.util.Properties;

public class BuildProcess extends CurrentProcess {
    private final AgentStatus agentStatus;

    public BuildProcess(FileCollectionFactory fileCollectionFactory) {
        super(fileCollectionFactory);
        // For purposes of this class, it is better to check if the agent is actually applied, regardless of the feature flag status.
        this.agentStatus = AgentStatus.allowed();
    }

    protected BuildProcess(JavaInfo jvm, JvmOptions effectiveJvmOptions, AgentStatus agentStatus) {
        super(jvm, effectiveJvmOptions);
        this.agentStatus = agentStatus;
    }

    /**
     * Attempts to configure the current process to run with the required build parameters.
     *
     * @return True if the current process could be configured, false otherwise.
     */
    public boolean configureForBuild(DaemonParameters requiredBuildParameters) {
        boolean javaHomeMatch = getJvm().equals(requiredBuildParameters.getEffectiveJvm());
        // Even if the agent is applied to this process, it is possible to run the build with the legacy instrumentation mode.
        boolean javaAgentStateMatch = agentStatus.isAgentInstrumentationEnabled() || !requiredBuildParameters.shouldApplyInstrumentationAgent();

        boolean immutableJvmArgsMatch = true;
        if (requiredBuildParameters.hasUserDefinedImmutableJvmArgs()) {
            List<String> effectiveSingleUseJvmArgs = requiredBuildParameters.getEffectiveSingleUseJvmArgs();
            logger.info(
                "Checking if the launcher JVM can be re-used for build. To be re-used, the launcher JVM needs to match the parameters required for the build process: {}",
                String.join(" ", effectiveSingleUseJvmArgs)
            );
            immutableJvmArgsMatch = getJvmOptions().getAllImmutableJvmArgs().equals(effectiveSingleUseJvmArgs);
        }
        if (javaHomeMatch && javaAgentStateMatch && immutableJvmArgsMatch && !isLowDefaultMemory(requiredBuildParameters)) {
            // Set the system properties and use this process
            Properties properties = new Properties();
            properties.putAll(requiredBuildParameters.getEffectiveSystemProperties());
            System.setProperties(properties);
            return true;
        }
        return false;
    }

    /**
     * Checks whether the current process is using the default client VM setting of 64m, which is too low to run the majority of builds.
     */
    private boolean isLowDefaultMemory(DaemonParameters daemonParameters) {
        if (daemonParameters.hasUserDefinedImmutableJvmArgs()) {
            for (String arg : daemonParameters.getEffectiveSingleUseJvmArgs()) {
                if (arg.startsWith("-Xmx")) {
                    return false;
                }
            }
        }
        return "64m".equals(getJvmOptions().getMaxHeapSize());
    }

    private final Logger logger = Logging.getLogger(BuildProcess.class);
}
