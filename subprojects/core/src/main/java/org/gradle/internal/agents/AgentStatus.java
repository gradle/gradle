/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.agents;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * A build service to query the status of the Gradle's Java agents. Prefer using this service to accessing the {@link AgentControl} directly.
 */
@ServiceScope(Scope.Global.class)
public interface AgentStatus {
    /**
     * Checks if the agent-based bytecode instrumentation is enabled for the current JVM process.
     *
     * @return {@code true} if the agent instrumentation should be used
     */
    boolean isAgentInstrumentationEnabled();
}
