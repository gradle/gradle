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
 * The deprecation timeline marker method that the scanner looks for, i.e. the terminal-ish
 * `DeprecationMessageBuilder` call that states when the deprecated thing changes.
 *
 * @param method the exact method name as it appears in source.
 * @param group the report section this marker belongs to.
 * @param description a human-readable explanation of what the marker announces.
 */
enum class RemovalTimeline(val method: String, val group: Group, val description: String) {
    REMOVED_IN_10("willBeRemovedInGradle10", Group.REMOVAL, "Scheduled to be removed in Gradle 10."),
    ERROR_IN_10("willBecomeAnErrorInGradle10", Group.ERROR, "Will become an error in Gradle 10."),
    ERROR_IN_NEXT_MAJOR("willBecomeAnErrorInNextMajorGradleVersion", Group.ERROR, "Will become an error in the next major Gradle version."),
    STARTING_IN_10("startingWithGradle10", Group.STARTING, "Behaviour changes starting with Gradle 10."),
    STARTING_IN_11("startingWithGradle11", Group.STARTING, "Behaviour changes starting with Gradle 11.");

    /**
     * The "starting with" markers take a `String` message argument; the others take none. Used so the
     * parser does not mistake that message for the deprecated symbol.
     */
    val takesMessageArgument: Boolean
        get() = this == STARTING_IN_10 || this == STARTING_IN_11

    enum class Group(val displayName: String) {
        REMOVAL("Removals"),
        ERROR("Behaviour changes to errors"),
        STARTING("Behaviour changes starting with a future version")
    }

    companion object {
        private val BY_METHOD = entries.associateBy { it.method }

        /** All marker method names the scanner matches. */
        val methodNames: Set<String> = entries.map { it.method }.toSet()

        fun fromMethod(method: String): RemovalTimeline? = BY_METHOD[method]

        fun fromString(value: String): RemovalTimeline? = entries.firstOrNull { it.name == value }
    }
}
