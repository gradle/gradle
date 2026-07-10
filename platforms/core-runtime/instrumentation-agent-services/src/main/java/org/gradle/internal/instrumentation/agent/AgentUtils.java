/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.instrumentation.agent;

/**
 * Common utilities used for Gradle's own Java agents.
 */
public final class AgentUtils {
    private AgentUtils() {}

    public static final String AGENT_MODULE_NAME = "gradle-instrumentation-agent";

    /**
     * Checks if the command-line argument looks like JVM switch that applies gradle instrumentation agent.
     * If the returned value is {@code true} then the argument is definitely a java agent application.
     * However, only the name of the agent jar is checked, so it is possible to have false positives and false negatives.
     *
     * @param jvmArg the argument to check
     * @return {@code true} if the argument looks like a switch, {@code false} otherwise
     */
    public static boolean isGradleInstrumentationAgentSwitch(String jvmArg) {
        return isJavaAgentSwitch(jvmArg) && jvmArg.contains(AGENT_MODULE_NAME);
    }

    /**
     * Matches any third-party agent switch (Java or native JVMTI) that could install a bytecode transformer.
     * Gradle's own instrumentation agent is excluded.
     */
    static boolean isThirdPartyAgentSwitch(String jvmArg) {
        if (isJavaAgentSwitch(jvmArg)) {
            return !jvmArg.contains(AGENT_MODULE_NAME);
        }
        return isJvmtiAgentSwitch(jvmArg);
    }

    private static boolean isJavaAgentSwitch(String jvmArg) {
        return jvmArg.startsWith("-javaagent:");
    }

    /**
     * Native JVMTI agents attached via {@code -agentlib:} or {@code -agentpath:} can subscribe to
     * {@code ClassFileLoadHook} and transform bytecode at class load, the same way a Java agent can.
     */
    private static boolean isJvmtiAgentSwitch(String jvmArg) {
        return jvmArg.startsWith("-agentlib:") || jvmArg.startsWith("-agentpath:");
    }
}
