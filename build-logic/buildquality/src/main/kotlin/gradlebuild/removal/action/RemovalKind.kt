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

package gradlebuild.removal.action


/**
 * The kind of deprecated subject, derived from the `DeprecationLogger.deprecate*` factory that
 * starts a deprecation chain. Mirrors the factory methods on `DeprecationLogger`.
 */
enum class RemovalKind {
    GENERIC,
    METHOD,
    INVOCATION,
    PROPERTY,
    SYSTEM_PROPERTY,
    TYPE,
    ACTION,
    BEHAVIOUR,
    TASK,
    TASK_TYPE,
    PLUGIN,
    INTERNAL_API,
    NAMED_PARAMETER,
    CONFIGURATION,
    OTHER;

    companion object {
        /** Maps a `DeprecationLogger.deprecate*` factory name to a [RemovalKind]. */
        fun fromFactory(factoryName: String): RemovalKind = when (factoryName) {
            "deprecate", "deprecateIndirectUsage", "deprecateBuildInvocationFeature" -> GENERIC
            "deprecateMethod" -> METHOD
            "deprecateInvocation" -> INVOCATION
            "deprecateProperty" -> PROPERTY
            "deprecateSystemProperty" -> SYSTEM_PROPERTY
            "deprecateType" -> TYPE
            "deprecateAction" -> ACTION
            "deprecateBehaviour" -> BEHAVIOUR
            "deprecateTask" -> TASK
            "deprecateTaskType" -> TASK_TYPE
            "deprecatePlugin" -> PLUGIN
            "deprecateInternalApi" -> INTERNAL_API
            "deprecateNamedParameter" -> NAMED_PARAMETER
            "deprecateConfiguration" -> CONFIGURATION
            else -> OTHER
        }

        fun fromString(value: String): RemovalKind =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}
