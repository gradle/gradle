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

package org.gradle.internal.cc.impl.fingerprint

import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.cc.impl.InputTrackingState
import org.gradle.internal.configuration.problems.IsolatedProjectsProblemsReporter
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.execution.WorkExecutionTracker
import org.gradle.internal.execution.WorkInputListeners
import org.gradle.internal.scripts.ScriptFileResolverListeners
import java.util.Properties


/**
 * A [ConfigurationCacheFingerprintEventHandler] that additionally reports system property mutations at configuration
 * time as Isolated Projects violations.
 *
 * Mutations are allowed until the root build's projects are created. Up until that point everything runs
 * on a single thread, including the init and settings scripts of the root build and of all included builds.
 */
internal
class IsolatedProjectsFingerprintEventHandler(
    workInputListeners: WorkInputListeners,
    scriptFileResolverListeners: ScriptFileResolverListeners,
    inputTrackingState: InputTrackingState,
    private val problems: IsolatedProjectsProblemsReporter,
    private val buildStateRegistry: BuildStateRegistry,
    private val configurationTimeBarrier: ConfigurationTimeBarrier,
    private val workExecutionTracker: WorkExecutionTracker
) : ConfigurationCacheFingerprintEventHandler(workInputListeners, scriptFileResolverListeners, inputTrackingState) {

    override fun systemPropertyChanged(key: Any, value: Any?, consumer: String?) {
        super.systemPropertyChanged(key, value, consumer)
        report(consumer) {
            text("mutation of system property ")
            reference(key.toString())
        }
    }

    override fun systemPropertyRemoved(key: Any, consumer: String?) {
        super.systemPropertyRemoved(key, consumer)
        report(consumer) {
            text("removal of system property ")
            reference(key.toString())
        }
    }

    override fun systemPropertiesCleared(consumer: String?) {
        super.systemPropertiesCleared(consumer)
        report(consumer) {
            text("clearing all system properties")
        }
    }

    override fun systemPropertiesReplaced(properties: Properties, consumer: String?) {
        super.systemPropertiesReplaced(properties, consumer)
        report(consumer) {
            text("replacing all system properties")
        }
    }

    private
    fun report(consumer: String?, message: StructuredMessage.Builder.() -> Unit) {
        if (!configurationTimeBarrier.isAtConfigurationTime || workExecutionTracker.isExecutingTaskOrTransformAction || !inputTrackingState.isEnabledForCurrentThread()) {
            return
        }
        if (!buildStateRegistry.rootBuild.isProjectsCreated) {
            return
        }
        problems.report {
            problem(consumer) { message() }.build()
        }
    }
}
