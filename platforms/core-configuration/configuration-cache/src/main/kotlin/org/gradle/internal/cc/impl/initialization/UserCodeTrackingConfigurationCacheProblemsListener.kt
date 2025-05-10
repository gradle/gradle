/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.internal.cc.impl.InputTrackingState
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.code.UserCodeSource
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.execution.WorkExecutionTracker
import java.util.concurrent.ConcurrentHashMap

class UserCodeTrackingConfigurationCacheProblemsListener internal constructor(
    problems: ProblemsListener,
    problemFactory: ProblemFactory,
    configurationTimeBarrier: ConfigurationTimeBarrier,
    workExecutionTracker: WorkExecutionTracker,
    inputTrackingState: InputTrackingState,
    private val userCodeApplicationContext: UserCodeApplicationContext
) : DefaultConfigurationCacheProblemsListener(problems, problemFactory, configurationTimeBarrier, workExecutionTracker, inputTrackingState),
    ConfigurationCacheIncompatibleUserCodeContext {

    private val notCompatibleUserCodeSources = ConcurrentHashMap<UserCodeSource, ProblemsListener>()

    override fun enterUserCode(userCodeSource: UserCodeSource, reason: String) {
        notCompatibleUserCodeSources.computeIfAbsent(userCodeSource) {
            problems.forIncompatibleBuildLogic(PropertyTrace.BuildLogic(userCodeSource), reason)
        }
    }

    override fun leaveUserCode(userCodeSource: UserCodeSource) {
        notCompatibleUserCodeSources.remove(userCodeSource)
    }

    override fun problemListenerForCurrentUserCode(): ProblemsListener {
        val userCodeSource = userCodeApplicationContext.current()?.source
        return userCodeSource?.let { notCompatibleUserCodeSources.getOrDefault(it, problems) }
            ?: problems
    }
}
