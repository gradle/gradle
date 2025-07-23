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

package org.gradle.internal.cc.impl

import org.gradle.internal.configuration.problems.StructuredMessage


/**
 * A build execution strategy chosen by Configuration Cache
 * that depends on the availability and state of existing cache entries
 * that correspond to the build environment and build parameters of the current invocation.
 */
internal sealed class ConfigurationCacheAction {

    /**
     * Whether this action is read-only, i.e., does not create/modify cache entries.
     */
    val isReadOnly: Boolean get() = when (this) {
        Store -> false
        is Update -> false
        else -> true
    }

    /**
     * Configuration cache entry is fully loaded and reused.
     */
    data class Load(val entryId: String) : ConfigurationCacheAction()

    /**
     * Configuration cache entry is loaded and partially reused.
     * The entry will be stored again, incrementally updating parts of state.
     */
    data class Update(val entryId: String, val invalidProjects: CheckedFingerprint.InvalidProjects) : ConfigurationCacheAction()

    /**
     * Configuration cache entry is invalid for the current invocation.
     * The new entry will be stored by the end of the build.
     */
    data object Store : ConfigurationCacheAction()

    /**
     * No valid entry was found and no entry should be created, as CC is in read-only mode.
     */
    data object SkipStore : ConfigurationCacheAction()

    fun withDescription(description: StructuredMessage): DescribedAction =
        DescribedAction(this, description)
}

internal data class DescribedAction(
    val action: ConfigurationCacheAction, val description: StructuredMessage
)
