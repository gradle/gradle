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

package org.gradle.internal.cc.impl.promo

import org.gradle.api.internal.ExternalProcessStartedListener
import org.gradle.internal.cc.impl.ConfigurationCacheInputsListener
import org.gradle.internal.cc.impl.Workarounds
import org.gradle.internal.cc.impl.initialization.ConfigurationCacheProblemsListener
import java.io.File

/**
 * This class is used to intercept instrumented calls when configuration cache is not enabled.
 * It is a lightweight alternative to the InstrumentedInputAccessListener because only the functionality used by the Promo controller is supported.
 */
internal class PromoInputsListener(
    configurationCacheProblemsListener: ConfigurationCacheProblemsListener,
) : ConfigurationCacheInputsListener {
    // TODO(mlopatkin): fold it into InstrumentedInputAccessListener if its overhead is reduced?
    private val externalProcessListener: ExternalProcessStartedListener = configurationCacheProblemsListener

    override fun systemPropertyQueried(key: String, value: Any?, consumer: String) = Unit

    override fun systemPropertyChanged(key: Any, value: Any?, consumer: String) = Unit

    override fun systemPropertyRemoved(key: Any, consumer: String) = Unit

    override fun systemPropertiesCleared(consumer: String) = Unit

    override fun envVariableQueried(key: String, value: String?, consumer: String) = Unit

    override fun externalProcessStarted(command: String, consumer: String) {
        if (Workarounds.canStartExternalProcesses(consumer)) {
            return
        }
        externalProcessListener.onExternalProcessStarted(command, consumer)
    }

    override fun fileOpened(file: File, consumer: String) = Unit

    override fun fileObserved(file: File, consumer: String) = Unit

    override fun fileSystemEntryObserved(file: File, consumer: String) = Unit

    override fun directoryContentObserved(file: File, consumer: String) = Unit

    override fun startParameterProjectPropertiesObserved() = Unit
}
