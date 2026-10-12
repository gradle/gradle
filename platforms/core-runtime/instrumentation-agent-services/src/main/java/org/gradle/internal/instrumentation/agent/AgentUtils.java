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

import java.util.function.Predicate;

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
        return isJavaAgentSwitchMatching(jvmArg, AgentUtils::isGradleInstrumentationAgent);
    }

    /**
     * Matches any third-party agent switch (Java or native JVMTI) that could feed Gradle's instrumentation agent with altered bytecode.
     * Gradle's own instrumentation agent is excluded, and so are the known non-instrumenting JVMTI agents.
     *
     * @see #isGradleInstrumentationAgent(String)
     * @see #isExemptAgentShortName(String)
     * @see #isExemptAgentLibraryFileName(String)
     */
    static boolean isThirdPartyAgentSwitch(String jvmArg) {
        // Java Agents usually transform bytecode.
        if (isJavaAgentSwitchMatching(jvmArg, agentJar -> !isGradleInstrumentationAgent(agentJar))) {
            // This is `-javaagent:` and not our own agent.
            return true;
        }

        // Native JVMTI agents attached via `-agentlib:` or `-agentpath:`
        // can transform bytecode at class load or redefine classes, the same way a Java agent can.
        // They run after Gradle's own instrumentation so their transformations are safe (though we don't detect inputs added by them).
        // However, when they redefine existing classes (and provide the bytecodes), these go through the full instrumentation pipeline.
        // Without runtime transformation capability, Gradle's agent will revert the changes back to the pre-instrumented original versions.
        // We cannot figure out if the native agent redefines classes, so we err on the side of caution.

        // That said, some JVMTI agents are used for debugging and profiling work, and we know they only redefine classes in some uncommon workflows.
        // Treating them as third-party agents would force buildscript instrumentation onto runtime transformation capability,
        // so a debugging or profiling session would exercise a different code path than a regular build does in production.
        // Exempt the ones we recognize so those sessions run the same instrumentation as production.
        // So far, we exclude:
        //  - JDWP (java debugger): it may redefine classes when attempting Hot Code Replace, but we emit a warning there, and it is not used often for Gradle work.
        //  - AsyncProfiler: it can only transform classes, but never redefines them (as of 4.5).
        if (isAgentSwitchMatching(jvmArg, "-agentlib:", libName -> !isExemptAgentShortName(libName))) {
            return true;
        }
        return isAgentSwitchMatching(jvmArg, "-agentpath:", libPath -> !isExemptAgentLibraryFileName(libPath));
    }

    private static boolean isJavaAgentSwitchMatching(String jvmArg, Predicate<String> agentPathCheck) {
        return isAgentSwitchMatching(jvmArg, "-javaagent:", agentPathCheck);
    }

    private static boolean isAgentSwitchMatching(String jvmArg, String switchPrefix, Predicate<String> agentCheck) {
        if (!jvmArg.startsWith(switchPrefix)) {
            return false;
        }
        int agentParamsPosition = jvmArg.indexOf('=', switchPrefix.length());
        String agent = jvmArg.substring(
            switchPrefix.length(),
            agentParamsPosition < 0 ? jvmArg.length() : agentParamsPosition
        );
        return agentCheck.test(agent);
    }

    /**
     * Checks if the JAR path points to Gradle's own instrumentation agent.
     */
    private static boolean isGradleInstrumentationAgent(String jarPath) {
        return jarPath.contains(AGENT_MODULE_NAME);
    }

    /**
     * Checks if the name is the name of the known exempt agent.
     *
     * @param name the short name in an {@code -agentlib:<name>} switch, as passed to {@code System.loadLibrary}
     */
    private static boolean isExemptAgentShortName(String name) {
        // These are only case-insensitive on some OSes,
        // but let's be generous rather than trying to figure out the exact OS.
        return name.equalsIgnoreCase("jdwp")             // JDWP debug agent
            || name.equalsIgnoreCase("asyncProfiler");   // async-profiler
    }

    /**
     * Checks if the path corresponds to the known exempt agent.
     *
     * @param agentLibPath the platform-specific path to agent's shared library
     */
    private static boolean isExemptAgentLibraryFileName(String agentLibPath) {
        String fileName = getFileName(agentLibPath);
        // We assume that Windows and macOS use case-insensitive file systems.
        // Filenames are OS-specific, so we can be more precise than isExemptAgentShortName for free.
        // equalsIgnoreCase uses generic case folding rules, which are fine for the ASCII strings we're dealing with here.

        // JDWP debug agent, shipped with the JDK.
        return fileName.equals("libjdwp.so")                        // Linux
            || fileName.equalsIgnoreCase("libjdwp.dylib")           // macOS
            || fileName.equalsIgnoreCase("jdwp.dll")                // Windows
            // async-profiler, which supports Linux and macOS only.
            || fileName.equals("libasyncProfiler.so")               // Linux
            || fileName.equalsIgnoreCase("libasyncProfiler.dylib"); // macOS
    }

    private static String getFileName(String path) {
        int separatorPos = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return separatorPos >= 0 ? path.substring(separatorPos + 1) : path;
    }
}
