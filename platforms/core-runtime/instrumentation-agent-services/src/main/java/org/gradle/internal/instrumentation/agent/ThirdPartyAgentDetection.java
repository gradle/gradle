/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.internal.lazy.Lazy;

import java.lang.management.ManagementFactory;

/**
 * Cached query that reports whether a non-Gradle agent switch was passed to the JVM at startup.
 * Covers both Java agents ({@code -javaagent:}) and native JVMTI agents
 * ({@code -agentlib:}, {@code -agentpath:}).
 *
 * <p>The result is computed on first call from the JVM's startup arguments and cached.
 * Agents attached at runtime via the Attach API are not detected and are not supported by
 * Gradle's buildscript instrumentation.
 */
public final class ThirdPartyAgentDetection {

    private static final Lazy<Boolean> PRESENT = Lazy.locking().of(() ->
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
            .anyMatch(AgentUtils::isThirdPartyAgentSwitch)
    );

    private ThirdPartyAgentDetection() {}

    public static boolean isThirdPartyAgentPresent() {
        return PRESENT.get();
    }
}
