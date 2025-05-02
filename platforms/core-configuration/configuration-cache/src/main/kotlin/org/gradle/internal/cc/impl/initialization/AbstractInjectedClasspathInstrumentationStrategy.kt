/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.cc.impl.initialization

import org.gradle.internal.instrumentation.agent.AgentUtils
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathInstrumentationStrategy
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathInstrumentationStrategy.TransformMode
import java.lang.management.ManagementFactory


abstract class AbstractInjectedClasspathInstrumentationStrategy : InjectedClasspathInstrumentationStrategy {
    override fun getTransform(): TransformMode {
        val isThirdPartyAgentPresent = ManagementFactory.getRuntimeMXBean().inputArguments.find { AgentUtils.isThirdPartyJavaAgentSwitch(it) } != null
        return if (isThirdPartyAgentPresent) {
            // Currently, the build logic instrumentation can interfere with Java agents, such as Jacoco
            // So, disable or fail or whatever based on which execution modes are enabled
            whenThirdPartyAgentPresent()
        } else {
            TransformMode.BUILD_LOGIC
        }
    }

    abstract fun whenThirdPartyAgentPresent(): TransformMode
}
