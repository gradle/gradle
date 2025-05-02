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

import org.gradle.internal.lazy.Lazy;

class DefaultAgentStatus implements AgentStatus {

    private static final Lazy<Boolean> IS_AGENT_INSTRUMENTATION_ENABLED = Lazy.locking().of(AgentControl::isInstrumentationAgentApplied);

    @Override
    public boolean isAgentInstrumentationEnabled() {
        return IS_AGENT_INSTRUMENTATION_ENABLED.get();
    }
}
